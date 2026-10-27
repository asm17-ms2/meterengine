import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

import type { IngestEventBatchResult } from "@meterengine/sdk";

import { parseEventCsv, toIngestEventInput, type CsvEventRow } from "./csv.ts";
import { toRowResults } from "./send-results.ts";

const HEADER = "transaction_id,customer_id,event_type,timestamp,properties,note";

function readSample(name: string): string {
  return readFileSync(join(import.meta.dirname, "../../..", name), "utf8");
}

function parseRows(text: string): CsvEventRow[] {
  const parsed = parseEventCsv(text);
  if ("error" in parsed) {
    assert.fail(parsed.error);
  }
  return parsed.rows;
}

test("sample-events.csv는 100행이고 오류가 없다", () => {
  const rows = parseRows(readSample("sample-events.csv"));
  assert.equal(rows.length, 100);
  assert.equal(rows.filter((row) => row.error !== undefined).length, 0);
  const counts = new Map<string, number>();
  for (const row of rows) {
    counts.set(row.transaction_id, (counts.get(row.transaction_id) ?? 0) + 1);
  }
  const duplicates = [...counts.values()].filter((count) => count > 1).reduce((sum, count) => sum + count - 1, 0);
  assert.equal(duplicates, 20);
  assert.deepEqual(rows[0].properties, { input_tokens: 101, output_tokens: 51, model: "claude-sonnet-5" });
  assert.equal(rows[0].line, 2);
});

test("sample-events-edge.csv는 10행이고 edge-007의 token은 문자열 그대로다", () => {
  const rows = parseRows(readSample("sample-events-edge.csv"));
  assert.equal(rows.length, 10);
  assert.equal(rows.filter((row) => row.error !== undefined).length, 0);
  const edge007 = rows.find((row) => row.transaction_id === "edge-007");
  assert.deepEqual(edge007?.properties, { token: "500" });
  assert.equal(edge007?.note, "token이 문자열이라 집계 제외(저장은 됨)");
  const edge006 = rows.find((row) => row.transaction_id === "edge-006");
  assert.deepEqual(edge006?.properties, { token: 12.5 });
});

test("따옴표 안 쉼표, 줄바꿈, 두 번 쓴 따옴표를 읽는다", () => {
  const text = [
    HEADER,
    'tx-1,c-1,llm_request,2026-10-01T00:00:00+09:00,"{""model"": ""a,b""}","첫 줄',
    '둘째 줄"',
    'tx-2,c-1,llm_request,2026-10-01T00:00:00+09:00,"{}","말한 ""따옴표"""',
  ].join("\n");
  const rows = parseRows(text);
  assert.equal(rows.length, 2);
  assert.deepEqual(rows[0].properties, { model: "a,b" });
  assert.equal(rows[0].note, "첫 줄\n둘째 줄");
  assert.equal(rows[0].line, 2);
  assert.equal(rows[1].note, '말한 "따옴표"');
  assert.equal(rows[1].line, 4);
});

test("BOM과 CRLF를 읽는다", () => {
  const text = `\uFEFF${HEADER}\r\ntx-1,c-1,llm_request,2026-10-01T00:00:00+09:00,"{""n"": 1}",메모\r\n`;
  const rows = parseRows(text);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].transaction_id, "tx-1");
  assert.equal(rows[0].note, "메모");
  assert.deepEqual(rows[0].properties, { n: 1 });
});

test("필수 컬럼이 빠지면 빠진 이름을 알려준다", () => {
  const parsed = parseEventCsv("transaction_id,event_type,properties\ntx-1,llm_request,{}\n");
  assert.ok("error" in parsed);
  assert.match(parsed.error, /customer_id/);
  assert.match(parsed.error, /timestamp/);
  assert.doesNotMatch(parsed.error, /event_type/);
});

test("컬럼 순서가 달라도 읽는다", () => {
  const text = 'properties,timestamp,event_type,customer_id,transaction_id\n"{""n"": 2}",2026-10-02T00:00:00Z,api_call,c-9,tx-9\n';
  const rows = parseRows(text);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].transaction_id, "tx-9");
  assert.equal(rows[0].customer_id, "c-9");
  assert.equal(rows[0].event_type, "api_call");
  assert.equal(rows[0].timestamp, "2026-10-02T00:00:00Z");
  assert.deepEqual(rows[0].properties, { n: 2 });
  assert.equal(rows[0].note, "");
});

test("properties가 JSON 객체가 아니면 그 행에만 오류를 둔다", () => {
  const text = [
    HEADER,
    "tx-1,c-1,llm_request,2026-10-01T00:00:00+09:00,[1],",
    "",
    "tx-2,c-1,llm_request,2026-10-01T00:00:00+09:00,{oops,",
    'tx-3,c-1,llm_request,2026-10-01T00:00:00+09:00,"{""n"": 3}",',
  ].join("\n");
  const rows = parseRows(text);
  assert.equal(rows.length, 3);
  assert.equal(rows[0].line, 2);
  assert.ok(rows[0].error);
  assert.equal(rows[0].properties, undefined);
  assert.equal(rows[0].properties_text, "[1]");
  assert.equal(rows[1].line, 4);
  assert.ok(rows[1].error);
  assert.equal(rows[2].line, 5);
  assert.equal(rows[2].error, undefined);
  assert.deepEqual(rows[2].properties, { n: 3 });
});

test("빈 transaction_id는 그대로 둔다", () => {
  const rows = parseRows(`${HEADER}\n,c-1,llm_request,,"{""n"": -5}",메모\n`);
  const input = toIngestEventInput(rows[0]);
  assert.equal(input.transaction_id, "");
  assert.deepEqual(input, {
    transaction_id: "",
    customer_id: "c-1",
    type: "llm_request",
    timestamp: "",
    properties: { n: -5 },
  });
  assert.equal("note" in input, false);
});

test("오류 행은 SKIP이고 나머지는 보낸 순서대로 결과와 짝짓는다", () => {
  const rows = parseRows(
    [
      HEADER,
      'tx-1,c-1,llm_request,2026-10-01T00:00:00+09:00,"{}",',
      "tx-2,c-1,llm_request,2026-10-01T00:00:00+09:00,nope,",
      'tx-3,c-1,llm_request,2026-10-01T00:00:00+09:00,"{}",',
      'tx-1,c-1,llm_request,2026-10-01T00:00:00+09:00,"{}",',
    ].join("\n"),
  );
  const results: IngestEventBatchResult[] = [
    { index: 0, transaction_id: "tx-1", result: "stored" },
    {
      index: 1,
      transaction_id: "tx-3",
      result: "rejected",
      error: { code: "validation_failed", message: "요청 값이 올바르지 않습니다" },
    },
    { index: 2, transaction_id: "tx-1", result: "duplicate" },
  ];
  const rowResults = toRowResults(rows, results);
  assert.deepEqual(
    rowResults.map((result) => [result.line, result.transaction_id, result.tag]),
    [
      [2, "tx-1", "NEW"],
      [3, "tx-2", "SKIP"],
      [4, "tx-3", "REJ"],
      [5, "tx-1", "DUP"],
    ],
  );
  assert.equal(rowResults[1].message, rows[1].error);
  assert.equal(rowResults[2].code, "validation_failed");
  assert.equal(rowResults[2].message, "요청 값이 올바르지 않습니다");
});

test("필드 중간의 따옴표는 글자 그대로 읽는다", () => {
  const rows = parseRows(
    [
      HEADER,
      'tx-1,c-1,llm_request,2026-10-01T00:00:00+09:00,"{}",5" screen',
      'tx-2,c-1,llm_request,2026-10-01T00:00:00+09:00,"{}",',
      'tx-3,c-1,llm_request,2026-10-01T00:00:00+09:00,"{}",',
    ].join("\n"),
  );
  assert.deepEqual(
    rows.map((row) => [row.line, row.transaction_id, row.note]),
    [
      [2, "tx-1", '5" screen'],
      [3, "tx-2", ""],
      [4, "tx-3", ""],
    ],
  );
});

test("따옴표가 닫히지 않은 채 파일이 끝나면 그 필드가 시작한 줄을 알려준다", () => {
  const parsed = parseEventCsv(
    [
      HEADER,
      'tx-1,c-1,llm_request,2026-10-01T00:00:00+09:00,"{""n"": 1},ok',
      "tx-2,c-1,llm_request,2026-10-01T00:00:00+09:00,{},",
    ].join("\n"),
  );
  assert.ok("error" in parsed);
  assert.match(parsed.error, /2번째 줄/);
});
