import type { Command } from "commander";
import { MeterEngineApiError } from "@meterengine/sdk";
import type { EventRejection, EventRejectionSearchResponse, MeterEngineClient } from "@meterengine/sdk";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { UsageError } from "../exit-code.ts";
import { assertMonth, kstMonthOf, previousMonth } from "../month.ts";
import { recordingStartedAfter } from "./diagnose-shared.ts";

// 수집 API의 transaction_id 규칙(255자 이하, NUL과 짝 없는 서로게이트 금지)과 거절 조회의 페이지 상한이다.
const MAX_TRANSACTION_ID_LENGTH = 255;
const REJECTION_PAGE_SIZE = 100;
// 요청 한도는 같은 키를 쓰는 앱과 함께 쓰므로 한 번에 너무 많이 보내지 않는다.
const MAX_TRANSACTION_IDS = 1000;
const CONCURRENCY = 4;
// SDK 재시도까지 다 쓰고도 429면 Retry-After(없으면 1초)만큼 기다려 요청 하나당 이만큼 더 보낸다.
const MAX_RATE_LIMIT_WAITS = 30;
const DEFAULT_RATE_LIMIT_WAIT_SECONDS = 1;
const MAX_REPORTED_PROBLEMS = 5;

interface RejectedEvent {
  transaction_id: string;
  code: string;
  error_fields: string[];
  recorded_at: string;
}

interface ParsedFile {
  ids: string[];
  duplicateLines: number;
}

export function registerVerify(program: Command, deps: CliDeps): void {
  const verify = program.command("verify").description("보낸 이벤트가 저장됐는지 대조한다");
  verify
    .command("sent")
    .description(
      "파일에 적은 transaction_id가 저장됐는지 대조해 stored, rejected, missing으로 나눈다 (GET /v1/events/lookup, GET /v1/event-rejections/search). " +
        "id마다 조회하므로 같은 키를 쓰는 앱과 분당 요청 한도를 나눠 쓴다. 429를 받으면 하나씩 기다리며 보낸다. 대조를 끝까지 하면 missing이 있어도 종료 코드 0이고 판정은 JSON에 있다",
    )
    .argument(
      "<file>",
      "줄마다 transaction_id 하나, 또는 transaction_id 칸이 있는 JSON 한 줄. 빈 줄과 #으로 시작하는 줄은 건너뛰고 줄 앞뒤 공백은 지운다. " +
        "앞뒤 공백이 있거나 #이나 {로 시작하는 id는 JSON 한 줄로 적는다. -면 표준 입력",
    )
    .option("--month <YYYY-MM>", "거절 기록을 찾을 달(보낸 달, KST). 여러 번 쓸 수 있다. 생략하면 이번 달과 지난달", collect)
    .action(async (file: string, options: { month?: string[] }, command: Command) => {
      const months = rejectionMonths(options.month ?? [], deps.now?.() ?? new Date());
      const parsed = parseTransactionIds(await readInput(deps, file));
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await verifySent(client, parsed, months));
    });
}

function collect(value: string, previous: string[] | undefined): string[] {
  return [...(previous ?? []), value];
}

function rejectionMonths(requested: string[], now: Date): string[] {
  const months = requested.length === 0 ? [kstMonthOf(now), previousMonth(kstMonthOf(now))] : requested.map(assertMonth);
  return [...new Set(months)].sort().reverse();
}

async function readInput(deps: CliDeps, path: string): Promise<string> {
  try {
    return await deps.readFile(path);
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    throw new UsageError("file_not_readable", `파일을 읽지 못했습니다: ${path} (${reason})`);
  }
}

function parseTransactionIds(text: string): ParsedFile {
  const seen = new Set<string>();
  const problems: string[] = [];
  let duplicateLines = 0;
  text
    .replace(/^﻿/, "")
    .split(/\r?\n/)
    .forEach((raw, index) => {
      const line = raw.trim();
      if (line === "" || line.startsWith("#")) return;
      const parsed = line.startsWith("{") ? idFromJson(line) : { id: line };
      const problem = "problem" in parsed ? parsed.problem : problemOf(parsed.id);
      if (problem !== undefined) problems.push(`${index + 1}번째 줄: ${problem}`);
      else if ("id" in parsed && seen.has(parsed.id)) duplicateLines += 1;
      else if ("id" in parsed) seen.add(parsed.id);
    });
  if (problems.length > 0) {
    const rest = problems.length > MAX_REPORTED_PROBLEMS ? ` 외 ${problems.length - MAX_REPORTED_PROBLEMS}줄` : "";
    throw new UsageError("invalid_input_line", `서버에 보내기 전에 멈췄습니다. ${problems.slice(0, MAX_REPORTED_PROBLEMS).join("; ")}${rest}`);
  }
  if (seen.size === 0) throw new UsageError("no_transaction_ids", "대조할 transaction_id가 없습니다. 줄마다 id 하나, 또는 transaction_id 칸이 있는 JSON을 적으세요");
  if (seen.size > MAX_TRANSACTION_IDS) {
    throw new UsageError("too_many_transaction_ids", `한 번에 ${MAX_TRANSACTION_IDS}개까지 대조합니다. 파일을 나눠 실행하세요 (지금 ${seen.size}개)`);
  }
  return { ids: [...seen], duplicateLines };
}

function idFromJson(line: string): { id: string } | { problem: string } {
  let value: unknown;
  try {
    value = JSON.parse(line);
  } catch {
    return { problem: "JSON으로 읽을 수 없습니다" };
  }
  if (typeof value !== "object" || value === null || !("transaction_id" in value) || typeof value.transaction_id !== "string") {
    return { problem: "transaction_id 문자열 칸이 없습니다" };
  }
  return { id: value.transaction_id };
}

function problemOf(id: string): string | undefined {
  if (id.trim() === "") return "transaction_id가 비어 있습니다";
  if (id.length > MAX_TRANSACTION_ID_LENGTH) return `transaction_id는 ${MAX_TRANSACTION_ID_LENGTH}자 이하입니다`;
  if (/\0|\p{Cs}/u.test(id)) return "transaction_id에 NUL 문자나 짝 없는 서로게이트가 있습니다";
  return undefined;
}

async function verifySent(client: MeterEngineClient, parsed: ParsedFile, months: string[]) {
  const gate = new RateLimitGate();
  const storedFlags = await gate.map(parsed.ids, (id) => isStored(client, gate, id));
  const notStored = parsed.ids.filter((_id, index) => !storedFlags[index]);
  const found = new Map<string, EventRejection>();
  const searched: string[] = [];
  let recordingStartedAt: string | null = null;
  // 같은 id가 여러 번 거절됐으면 가장 최근 달의 가장 최근 기록을 쓴다.
  for (const month of months) {
    const unresolved = notStored.filter((id) => !found.has(id));
    if (unresolved.length === 0) break;
    const result = await findRejections(client, gate, month, unresolved);
    searched.push(month);
    recordingStartedAt ??= result.recordingStartedAt;
    for (const [id, rejection] of result.found) found.set(id, rejection);
  }
  const rejected: RejectedEvent[] = notStored.flatMap((id) => {
    const rejection = found.get(id);
    return rejection === undefined ? [] : [{ transaction_id: id, code: rejection.error_code, error_fields: rejection.error_fields, recorded_at: rejection.recorded_at }];
  });
  const missing = notStored.filter((id) => !found.has(id));
  return {
    total: parsed.ids.length,
    stored: parsed.ids.filter((_id, index) => storedFlags[index]),
    rejected,
    missing,
    duplicate_lines: parsed.duplicateLines,
    rejection_months: searched,
    notes: missing.length === 0 ? [] : missingNotes(months, recordingStartedAt),
  };
}

async function isStored(client: MeterEngineClient, gate: RateLimitGate, id: string): Promise<boolean> {
  try {
    await gate.call(() => client.getEvent(id));
    return true;
  } catch (error) {
    if (error instanceof MeterEngineApiError && error.status === 404 && error.code === "event_not_found") return false;
    throw error;
  }
}

// 달 전체 목록을 끝까지 받는 것과 id마다 q로 찾는 것 가운데 요청이 적은 쪽을 고른다.
async function findRejections(client: MeterEngineClient, gate: RateLimitGate, month: string, ids: string[]) {
  const wanted = new Set(ids);
  const found = new Map<string, EventRejection>();
  const first = await searchRejections(client, gate, month, 0);
  collectExact(first.rejections, wanted, found);
  const remaining = ids.filter((id) => !found.has(id));
  const restPages = Math.ceil(first.total / REJECTION_PAGE_SIZE) - 1;
  if (remaining.length > 0 && restPages > 0) {
    if (restPages <= remaining.length) {
      const pages = Array.from({ length: restPages }, (_value, index) => index + 1);
      const responses = await gate.map(pages, (page) => searchRejections(client, gate, month, page));
      for (const response of responses) collectExact(response.rejections, wanted, found);
      // 받는 동안 기록 수가 바뀌면 행이 페이지 경계를 넘어 빠질 수 있어 못 찾은 id만 q로 다시 찾는다.
      if (responses.some((response) => response.total !== first.total)) {
        await searchEach(client, gate, month, ids.filter((id) => !found.has(id)), found);
      }
    } else {
      await searchEach(client, gate, month, remaining, found);
    }
  }
  return { found, recordingStartedAt: first.recording_started_at };
}

async function searchEach(client: MeterEngineClient, gate: RateLimitGate, month: string, ids: string[], found: Map<string, EventRejection>): Promise<void> {
  const matches = await gate.map(ids, (id) => searchRejectionById(client, gate, month, id));
  ids.forEach((id, index) => {
    const match = matches[index];
    if (match !== undefined) found.set(id, match);
  });
}

// q는 transaction_id와 type의 부분 일치라서 transaction_id가 정확히 같은 것만 고른다.
async function searchRejectionById(client: MeterEngineClient, gate: RateLimitGate, month: string, id: string): Promise<EventRejection | undefined> {
  for (let page = 0; ; page += 1) {
    const response = await searchRejections(client, gate, month, page, id);
    const match = response.rejections.find((rejection) => rejection.transaction_id === id);
    if (match !== undefined) return match;
    if ((page + 1) * REJECTION_PAGE_SIZE >= response.total) return undefined;
  }
}

function searchRejections(client: MeterEngineClient, gate: RateLimitGate, month: string, page: number, q?: string): Promise<EventRejectionSearchResponse> {
  return gate.call(() => client.searchEventRejections({ month, q, page, size: REJECTION_PAGE_SIZE }));
}

// 목록은 최근 기록부터 오므로 처음 본 기록을 남긴다.
function collectExact(rejections: EventRejection[], wanted: Set<string>, found: Map<string, EventRejection>): void {
  for (const rejection of rejections) {
    const id = rejection.transaction_id;
    if (id !== null && wanted.has(id) && !found.has(id)) found.set(id, rejection);
  }
}

function missingNotes(months: string[], recordingStartedAt: string | null): string[] {
  const notes = [
    "missing은 저장 기록도 거절 기록도 찾지 못한 id입니다. 서버가 수집을 202로 받는 설정이면 저장은 뒤에서 하므로 아직 조회되지 않을 수 있습니다. 잠시 뒤 다시 대조하세요",
    "JSON 형식이나 입력 검증에서 거절된 요청(400), 인증과 요청 한도로 거절된 요청은 서버에 기록되지 않습니다. 다시 보낼 때는 같은 transaction_id를 쓰세요. 이미 저장된 이벤트는 다시 저장되지 않습니다",
    `거절 기록은 ${months.join(", ")}에 받은 요청에서만 찾았습니다. 다른 달에 보냈다면 --month로 그 달을 지정하세요`,
  ];
  if (recordingStartedAfter(months, recordingStartedAt)) {
    notes.push(`서버는 ${recordingStartedAt}부터 거절을 기록했습니다. 그 전에 거절된 요청은 missing으로 나옵니다`);
  }
  return notes;
}

// 처음에는 CONCURRENCY개씩 보내다가 429를 한 번 받으면 끝날 때까지 하나씩 보낸다.
class RateLimitGate {
  private concurrency = CONCURRENCY;

  async call<R>(request: () => Promise<R>): Promise<R> {
    for (let waits = 0; ; waits += 1) {
      try {
        return await request();
      } catch (error) {
        if (!(error instanceof MeterEngineApiError && error.status === 429) || waits >= MAX_RATE_LIMIT_WAITS) throw error;
        this.concurrency = 1;
        await sleep((error.retryAfterSeconds ?? DEFAULT_RATE_LIMIT_WAIT_SECONDS) * 1000);
      }
    }
  }

  // 하나가 실패하면 새 작업을 시작하지 않고 그 오류를 올린다.
  async map<T, R>(items: T[], task: (item: T) => Promise<R>): Promise<R[]> {
    const results: R[] = new Array<R>(items.length);
    let next = 0;
    let hasFailed = false;
    const worker = async (slot: number) => {
      while (!hasFailed && slot < this.concurrency && next < items.length) {
        const index = next;
        next += 1;
        try {
          results[index] = await task(items[index]);
        } catch (error) {
          hasFailed = true;
          throw error;
        }
      }
    };
    await Promise.all(Array.from({ length: Math.min(this.concurrency, items.length) }, (_value, slot) => worker(slot)));
    return results;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
