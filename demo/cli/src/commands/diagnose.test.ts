import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import type { Call, Reply } from "../testing.ts";
import { looksLikeTypo } from "./diagnose-month.ts";
import { TEST_KEY, json, setup } from "../testing.ts";

const CUSTOMER_ID = "0b6f1c1e-3d5a-4c2b-9f7e-2a1d4c6b8e90";
const NOW = new Date("2026-11-14T03:00:00Z");

const EVENT = {
  transaction_id: "tx-1",
  customer_id: CUSTOMER_ID,
  customer_name: "OpenAB",
  type: "llm_request",
  properties: { input_tokens: 10, output_tokens: 20, model: "ab-1" },
  occurred_at: "2026-11-14T11:00:00+09:00",
  received_at: "2026-11-14T11:00:01.204+09:00",
};

function usage(code: string, eventType: string) {
  return { code, name: code, event_type: eventType, aggregation: "sum", target_property: code, customers: [] };
}

// 서버가 그 달에 집계하는 미터다. 보관한 retired는 서버가 빼고 준다.
const USAGE = {
  month: "2026-11",
  billable_metric_usages: [usage("input-tokens", "llm_request"), usage("output-tokens", "llm_request"), usage("api-calls", "api_call")],
};

function draft(customers: unknown[] = []) {
  return { month: "2026-11", calculated_at: "2026-11-14T12:00:00+09:00", auto_finalize_enabled: true, auto_finalize_at: null, auto_finalize_running: false, total_amount: 0, customers };
}

function draftCustomer(extra: Record<string, unknown> = {}) {
  return { customer_id: CUSTOMER_ID, customer_name: "OpenAB", amount: 0, lines: [], unpriced_usages: [], negative_usages: [], auto_finalize_held: false, ...extra };
}

function issue(transactionId: string, reason: string, code: string | null, extra: Record<string, unknown> = {}) {
  const hints: Record<string, string> = {
    unknown_event_type: "이벤트 유형에 맞는 미터를 정의하거나 전송 유형을 확인하세요",
    invalid_target_property: "미터의 수량 속성과 전송 값의 형식을 확인하세요",
    unpriced_usage: "고객, 미터와 차원에 적용되는 단가를 등록하세요",
  };
  return {
    transaction_id: transactionId,
    customer_id: CUSTOMER_ID,
    customer_name: "OpenAB",
    type: "llm_request",
    occurred_at: EVENT.occurred_at,
    received_at: EVENT.received_at,
    reason,
    billable_metric_code: code,
    target_property: code,
    action_hint: hints[reason],
    negative_quantity: false,
    ...extra,
  };
}

function issuePage(issues: unknown[], total = issues.length, page = 0) {
  return {
    month: "2026-11",
    cutoff_at: "2026-11-14T12:00:00+09:00",
    page,
    size: 100,
    total,
    issues,
    summary: { unknown_event_type: 0, invalid_target_property: 0, unpriced_usage: 0 },
    warnings: { scope: "organization_month", future_event_count: 0, negative_event_metric_count: 0 },
  };
}

function rejectionPage(month: string, rejections: unknown[], total = rejections.length) {
  return { month, recording_started_at: "2026-09-01T00:00:00Z", page: 0, size: 100, total, rejections };
}

function rejection(transactionId: string | null, code: string) {
  return {
    id: "6c1f2b3a-0d4e-4f5a-8b6c-7d8e9f0a1b2c",
    transaction_id: transactionId,
    customer_id: null,
    type: "llm_request",
    occurred_at: "2026-11-13T10:00:00+09:00",
    error_code: code,
    error_fields: ["customer_id"],
    request_id: null,
    batch_index: null,
    recorded_at: "2026-11-13T10:00:01+09:00",
  };
}

const NOT_FOUND = { code: "event_not_found", message: "이벤트를 찾을 수 없습니다" };

class Handler {
  readonly handle: (query: URLSearchParams) => Response;

  constructor(handle: (query: URLSearchParams) => Response) {
    this.handle = handle;
  }
}

// 경로마다 200 본문을 주는 가짜 서버다. respond로 감싼 경로는 쿼리를 보고 응답을 만든다.
function respond(handle: (query: URLSearchParams) => Response): Handler {
  return new Handler(handle);
}

function server(routes: Record<string, unknown>): Reply[] {
  const reply = (url: string) => {
    const { pathname, searchParams } = new URL(url);
    if (!(pathname in routes)) return new Error(`예상하지 않은 호출: ${url}`);
    const route = routes[pathname];
    return route instanceof Handler ? route.handle(searchParams) : json(200, route);
  };
  return Array.from({ length: 40 }, () => reply);
}

function requests(calls: Call[]): Array<[string, Record<string, string>]> {
  return calls.map((call) => {
    const url = new URL(call.url);
    return [url.pathname, Object.fromEntries(url.searchParams)];
  });
}

const ISSUE_QUERY = { month: "2026-11", customer_id: CUSTOMER_ID, q: "tx-1", page: "0", size: "100" };

test("diagnose event는 저장됐고 누락 사유가 없으면 청구에 든다고 판정하고 활성 미터를 함께 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setup(
    server({ "/v1/events/lookup": EVENT, "/v1/invoices": { invoices: [] }, "/v1/usage": USAGE, "/v1/invoices/draft": draft([draftCustomer()]), "/v1/event-issues/search": issuePage([]) }),
    { now: NOW },
  );

  const code = await run(["diagnose", "event", "tx-1"], deps);

  assert.equal(code, EXIT_OK);
  assert.equal(err.join(""), "");
  assert.deepEqual(stdoutJson(), {
    transaction_id: "tx-1",
    stored: true,
    billable: true,
    month: "2026-11",
    findings: [],
    notes: [],
    billable_metrics: { included: ["input-tokens", "output-tokens"], excluded: [] },
  });
  assert.deepEqual(requests(calls), [
    ["/v1/events/lookup", { transaction_id: "tx-1" }],
    ["/v1/invoices", { month: "2026-11", customer_id: CUSTOMER_ID }],
    ["/v1/event-issues/search", ISSUE_QUERY],
    ["/v1/usage", { month: "2026-11" }],
    ["/v1/invoices/draft", { month: "2026-11" }],
  ]);
  assert.ok(calls.every((call) => call.method === "GET" && call.headers.get("Authorization") === `Bearer ${TEST_KEY}`));
});

test("diagnose event는 유형 오타를 transaction_id가 정확히 같은 행만 골라 청구 제외로 판정하고 미터는 조회하지 않는다", async () => {
  const typo = { ...EVENT, type: "llm_requests" };
  const rows = [issue("tx-10", "unknown_event_type", null, { type: "llm_requests" }), issue("tx-1", "unknown_event_type", null, { type: "llm_requests" })];
  const { deps, calls, stdoutJson } = await setup(
    server({ "/v1/events/lookup": typo, "/v1/invoices": { invoices: [] }, "/v1/usage": USAGE, "/v1/event-issues/search": issuePage(rows) }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), {
    transaction_id: "tx-1",
    stored: true,
    billable: false,
    month: "2026-11",
    findings: [
      {
        source: "event_issues",
        reason: "unknown_event_type",
        type: "llm_requests",
        billable_metric_code: null,
        target_property: null,
        action_hint: "이벤트 유형에 맞는 미터를 정의하거나 전송 유형을 확인하세요",
      },
    ],
    notes: [],
  });
  assert.ok(!requests(calls).some(([path]) => path === "/v1/usage"));
});

test("diagnose event는 미터 하나만 단가가 없으면 나머지 미터로는 청구된다고 나눠 낸다", async () => {
  const { deps, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": EVENT,
      "/v1/invoices": { invoices: [] },
      "/v1/usage": USAGE, "/v1/invoices/draft": draft([draftCustomer()]),
      "/v1/event-issues/search": issuePage([issue("tx-1", "unpriced_usage", "output-tokens")]),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; billable_metrics: unknown; findings: Array<{ reason: string; billable_metric_code: string }> };
  assert.equal(result.billable, true);
  assert.deepEqual(result.billable_metrics, { included: ["input-tokens"], excluded: ["output-tokens"] });
  assert.deepEqual(
    result.findings.map((finding) => [finding.reason, finding.billable_metric_code]),
    [["unpriced_usage", "output-tokens"]],
  );
});

test("diagnose event는 걸린 미터 모두에 사유가 있으면 청구 제외로 판정한다", async () => {
  const rows = [issue("tx-1", "invalid_target_property", "input-tokens"), issue("tx-1", "invalid_target_property", "output-tokens")];
  const { deps, stdoutJson } = await setup(
    server({ "/v1/events/lookup": EVENT, "/v1/invoices": { invoices: [] }, "/v1/usage": USAGE, "/v1/event-issues/search": issuePage(rows) }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; billable_metrics: unknown; findings: unknown[] };
  assert.equal(result.billable, false);
  assert.deepEqual(result.billable_metrics, { included: [], excluded: ["input-tokens", "output-tokens"] });
  assert.equal(result.findings.length, 2);
});

test("diagnose event는 정확히 같은 행이 다음 페이지에 있으면 페이지를 넘겨 찾는다", async () => {
  const others = Array.from({ length: 100 }, (_, index) => issue(`tx-1${index}`, "unknown_event_type", null));
  const { deps, calls, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": EVENT,
      "/v1/invoices": { invoices: [] },
      "/v1/usage": USAGE, "/v1/invoices/draft": draft([draftCustomer()]),
      "/v1/event-issues/search": respond((query) =>
        json(200, query.get("page") === "0" ? issuePage(others, 101) : issuePage([issue("tx-1", "unpriced_usage", "input-tokens")], 101, 1)),
      ),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; findings: Array<{ reason: string }> };
  assert.deepEqual(
    result.findings.map((finding) => finding.reason),
    ["unpriced_usage"],
  );
  assert.equal(result.billable, true);
  assert.deepEqual(
    requests(calls)
      .filter(([path]) => path === "/v1/event-issues/search")
      .map(([, query]) => query.page),
    ["0", "1"],
  );
});

test("diagnose event는 발생 시각의 KST 달로 조회한다", async () => {
  const boundary = { ...EVENT, occurred_at: "2026-10-31T15:30:00Z", received_at: "2026-10-31T15:30:01Z" };
  const { deps, calls } = await setup(
    server({ "/v1/events/lookup": boundary, "/v1/invoices": { invoices: [] }, "/v1/usage": USAGE, "/v1/invoices/draft": draft([draftCustomer()]), "/v1/event-issues/search": issuePage([]) }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1", "--month", "2026-09"], deps), EXIT_OK);
  assert.equal(requests(calls)[1][1].month, "2026-11");
  assert.equal(requests(calls)[2][1].month, "2026-11");
});

test("diagnose event는 집계 마감 뒤에 받은 이벤트를 다른 조회 없이 청구 제외로 판정한다", async () => {
  const late = { ...EVENT, occurred_at: "2026-10-31T23:00:00+09:00", received_at: "2026-11-02T00:00:00+09:00" };
  const { deps, calls, stdoutJson } = await setup(server({ "/v1/events/lookup": late }), { now: NOW });

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  assert.equal(calls.length, 1);
  const result = stdoutJson() as { billable: boolean; month: string; findings: Array<Record<string, unknown>> };
  assert.equal(result.billable, false);
  assert.equal(result.month, "2026-10");
  assert.equal(result.findings[0].reason, "received_after_cutoff");
  assert.equal(result.findings[0].cutoff_at, "2026-11-02T00:00:00+09:00");
});

test("diagnose event는 발생 시각이 아직 오지 않은 이벤트를 판정 보류(null)로 낸다", async () => {
  const future = { ...EVENT, occurred_at: "2026-11-20T09:00:00+09:00" };
  const { deps, calls, stdoutJson } = await setup(server({ "/v1/events/lookup": future }), { now: NOW });

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  assert.equal(calls.length, 1);
  const result = stdoutJson() as { billable: null; findings: Array<{ reason: string }> };
  assert.equal(result.billable, null);
  assert.equal(result.findings[0].reason, "future_timestamp");
});

test("diagnose event는 그 달 활성 미터를 사용량 응답에서 골라, 보관했다 복원한 미터도 청구 미터로 센다", async () => {
  const restored = { ...EVENT, type: "rv_rr" };
  const { deps, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": restored,
      "/v1/invoices": { invoices: [] },
      "/v1/usage": { month: "2026-11", billable_metric_usages: [usage("rv-rr", "rv_rr"), usage("rv-rr2", "rv_rr")] },
      "/v1/event-issues/search": issuePage([issue("tx-1", "invalid_target_property", "rv-rr2", { type: "rv_rr" })]),
      "/v1/invoices/draft": draft([draftCustomer()]),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; billable_metrics: unknown };
  assert.equal(result.billable, true);
  assert.deepEqual(result.billable_metrics, { included: ["rv-rr"], excluded: ["rv-rr2"] });
});

test("diagnose event는 고객의 미터와 차원 값 조합 합계가 음수라 금액에서 빠진 미터를 negative_total로 뺀다", async () => {
  const negatives = [
    { billable_metric_code: "input-tokens", target_property: "input-tokens", dimension_values: { model: "ab-1" }, quantity: -3 },
    { billable_metric_code: "output-tokens", target_property: "output-tokens", dimension_values: { model: "ab-2" }, quantity: -1 },
  ];
  const other = { ...draftCustomer({ customer_id: "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f" }), negative_usages: [{ ...negatives[1], dimension_values: { model: "ab-1" } }] };
  const { deps, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": EVENT,
      "/v1/invoices": { invoices: [] },
      "/v1/usage": USAGE,
      "/v1/event-issues/search": issuePage([]),
      "/v1/invoices/draft": draft([other, draftCustomer({ negative_usages: negatives })]),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), {
    transaction_id: "tx-1",
    stored: true,
    billable: true,
    month: "2026-11",
    findings: [
      {
        source: "draft_invoice",
        reason: "negative_total",
        billable_metric_code: "input-tokens",
        dimension_values: { model: "ab-1" },
        quantity: -3,
        action_hint: "이 고객의 이 미터와 차원 값 조합은 그 달 합계가 음수라 금액에 들지 않습니다. 정정 이벤트의 수량을 확인하세요",
      },
    ],
    notes: [],
    billable_metrics: { included: ["output-tokens"], excluded: ["input-tokens"] },
  });
});

test("diagnose event는 남은 미터가 모두 음수 합계면 청구 제외로 판정한다", async () => {
  const negative = (code: string) => ({ billable_metric_code: code, target_property: code, dimension_values: {}, quantity: -2 });
  const { deps, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": EVENT,
      "/v1/invoices": { invoices: [] },
      "/v1/usage": USAGE,
      "/v1/event-issues/search": issuePage([issue("tx-1", "unpriced_usage", "output-tokens")]),
      "/v1/invoices/draft": draft([draftCustomer({ negative_usages: [negative("input-tokens"), negative("output-tokens")] })]),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; billable_metrics: unknown; findings: Array<Record<string, unknown>> };
  assert.equal(result.billable, false);
  assert.deepEqual(result.billable_metrics, { included: [], excluded: ["output-tokens", "input-tokens"] });
  assert.deepEqual(
    result.findings.map((finding) => [finding.reason, finding.billable_metric_code]),
    [
      ["unpriced_usage", "output-tokens"],
      ["negative_total", "input-tokens"],
    ],
  );
});

test("diagnose event는 사유 조회가 페이지 상한에 걸리고 이 이벤트 사유를 못 찾으면 판정을 보류한다", async () => {
  const others = Array.from({ length: 100 }, (_, index) => issue(`tx-1${index}`, "unpriced_usage", "input-tokens"));
  const { deps, calls, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": EVENT,
      "/v1/invoices": { invoices: [] },
      "/v1/usage": USAGE,
      "/v1/event-issues/search": respond((query) => json(200, issuePage(others, 5000, Number(query.get("page"))))),
      "/v1/invoices/draft": draft([draftCustomer()]),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: null; findings: unknown[]; notes: string[] };
  assert.equal(result.billable, null);
  assert.deepEqual(result.findings, []);
  assert.deepEqual(result.notes, ["청구 누락 사유를 조회 상한까지만 확인했습니다. 빠진 사유가 있을 수 있습니다"]);
  assert.equal(requests(calls).filter(([path]) => path === "/v1/event-issues/search").length, 10);
});

test("diagnose event는 그 유형의 활성 미터도 사유도 없으면 판정을 보류하고 이유를 notes에 적는다", async () => {
  const { deps, calls, stdoutJson } = await setup(
    server({ "/v1/events/lookup": EVENT, "/v1/invoices": { invoices: [] }, "/v1/usage": { month: "2026-11", billable_metric_usages: [] }, "/v1/event-issues/search": issuePage([]) }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as Record<string, unknown>;
  assert.equal(result.billable, null);
  assert.equal(result.billable_metrics, undefined);
  assert.deepEqual(result.notes, ["이 유형을 집계하는 미터를 찾지 못했지만 서버가 청구 누락 사유도 남기지 않아 판정하지 못했습니다"]);
  assert.ok(!requests(calls).some(([path]) => path === "/v1/invoices/draft"));
});

const INVOICE = {
  id: "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
  invoice_number: "INV-2026-10-0001",
  customer_id: CUSTOMER_ID,
  customer_name: "OpenAB",
  month: "2026-10",
  cutoff_at: "2026-10-20T10:00:00+09:00",
  finalized_at: "2026-10-20T10:00:00+09:00",
  supply_amount: 1000,
  tax_amount: 100,
  total_amount: 1100,
  lines: [],
};

test("diagnose event는 인보이스를 먼저 확정한 뒤 받은 이벤트를 청구 제외로 판정한다", async () => {
  const afterInvoice = { ...EVENT, occurred_at: "2026-10-25T10:00:00+09:00", received_at: "2026-10-25T10:00:01+09:00" };
  const { deps, calls, stdoutJson } = await setup(server({ "/v1/events/lookup": afterInvoice, "/v1/invoices": { invoices: [INVOICE] } }), { now: NOW });

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  assert.equal(calls.length, 2);
  const result = stdoutJson() as { billable: boolean; invoice: unknown; findings: Array<Record<string, unknown>> };
  assert.equal(result.billable, false);
  assert.deepEqual(result.invoice, { id: INVOICE.id, invoice_number: INVOICE.invoice_number, finalized_at: INVOICE.finalized_at, cutoff_at: INVOICE.cutoff_at });
  assert.equal(result.findings[0].reason, "after_invoice_cutoff");
});

function line(code: string, dimensionValues: Record<string, unknown> = {}) {
  return { billable_metric_code: code, target_property: code, dimension_values: dimensionValues, quantity: 10, unit_price: 1, amount: 10, line_type: "per_unit", tier_index: 0 };
}

const BEFORE_INVOICE = { ...EVENT, occurred_at: "2026-10-10T10:00:00+09:00", received_at: "2026-10-10T10:00:01+09:00" };

function invoiceServer(lines: unknown[], issues: unknown[] = []) {
  return server({
    "/v1/events/lookup": BEFORE_INVOICE,
    "/v1/invoices": { invoices: [{ ...INVOICE, lines }] },
    "/v1/usage": { ...USAGE, month: "2026-10" },
    "/v1/event-issues/search": issuePage(issues),
  });
}

test("diagnose event는 확정한 달이면 인보이스 라인에 이벤트의 미터와 차원 값이 있는지로 판정하고 청구서는 다시 계산하지 않는다", async () => {
  const { deps, calls, stdoutJson } = await setup(invoiceServer([line("input-tokens", { model: "ab-1" }), line("output-tokens", { model: "ab-2" })]), { now: NOW });

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; notes: string[]; invoice: { id: string }; billable_metrics: unknown; findings: Array<Record<string, unknown>> };
  assert.equal(result.billable, true);
  assert.equal(result.invoice.id, INVOICE.id);
  assert.equal(result.notes.length, 1);
  assert.deepEqual(result.billable_metrics, { included: ["input-tokens"], excluded: ["output-tokens"] });
  assert.deepEqual(
    result.findings.map((finding) => [finding.reason, finding.billable_metric_code]),
    [["not_in_finalized_invoice", "output-tokens"]],
  );
  assert.ok(!requests(calls).some(([path]) => path === "/v1/invoices/draft"));
});

test("diagnose event는 확정 뒤 단가를 등록해 지금은 사유가 없어도 인보이스 라인에 없으면 청구 제외로 판정한다", async () => {
  const { deps, stdoutJson } = await setup(invoiceServer([]), { now: NOW });

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; billable_metrics: unknown; findings: Array<Record<string, unknown>> };
  assert.equal(result.billable, false);
  assert.deepEqual(result.billable_metrics, { included: [], excluded: ["input-tokens", "output-tokens"] });
  assert.deepEqual(
    result.findings.map((finding) => [finding.source, finding.reason, finding.billable_metric_code, finding.invoice_id]),
    [
      ["invoice", "not_in_finalized_invoice", "input-tokens", INVOICE.id],
      ["invoice", "not_in_finalized_invoice", "output-tokens", INVOICE.id],
    ],
  );
});

test("diagnose event는 확정한 달의 단가 사유를 인보이스 라인과 대조해 라인에 있으면 버리고 없으면 사유로 낸다", async () => {
  const issues = [issue("tx-1", "unpriced_usage", "input-tokens"), issue("tx-1", "unpriced_usage", "output-tokens"), issue("tx-1", "invalid_target_property", "api-calls")];
  const { deps, stdoutJson } = await setup(invoiceServer([line("input-tokens", { model: "ab-1" })], issues), { now: NOW });

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { billable: boolean; billable_metrics: unknown; findings: Array<Record<string, unknown>> };
  assert.equal(result.billable, true);
  assert.deepEqual(
    result.findings.map((finding) => [finding.source, finding.reason, finding.billable_metric_code]),
    [
      ["event_issues", "invalid_target_property", "api-calls"],
      ["event_issues", "unpriced_usage", "output-tokens"],
    ],
  );
  assert.deepEqual(result.billable_metrics, { included: ["input-tokens"], excluded: ["api-calls", "output-tokens"] });
});

test("diagnose event는 저장 안 된 이벤트의 거절 기록을 이번 달과 지난달에서 정확히 같은 id로 찾는다", async () => {
  const { deps, calls, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": respond(() => json(404, NOT_FOUND)),
      "/v1/event-rejections/search": respond((query) =>
        json(200, query.get("month") === "2026-11" ? rejectionPage("2026-11", [rejection("tx-1-retry", "validation_error"), rejection("tx-1", "customer_not_found")]) : rejectionPage("2026-10", [])),
      ),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), {
    transaction_id: "tx-1",
    stored: false,
    billable: false,
    month: null,
    findings: [
      {
        source: "event_rejections",
        reason: "customer_not_found",
        error_fields: ["customer_id"],
        recorded_at: "2026-11-13T10:00:01+09:00",
        batch_index: null,
        action_hint: "customer_id에 MeterEngine 고객 UUID를 넣었는지 확인하세요. 외부 ID라면 customers list --external-id로 UUID를 찾습니다",
      },
    ],
    notes: [],
  });
  assert.deepEqual(requests(calls), [
    ["/v1/events/lookup", { transaction_id: "tx-1" }],
    ["/v1/event-rejections/search", { month: "2026-11", q: "tx-1", page: "0", size: "100" }],
    ["/v1/event-rejections/search", { month: "2026-10", q: "tx-1", page: "0", size: "100" }],
  ]);
});

test("diagnose event는 어디에도 기록이 없으면 형식 거절과 비동기 저장 가능성을 함께 알리고 종료 코드 0이다", async () => {
  const { deps, calls, stdoutJson } = await setup(
    server({ "/v1/events/lookup": respond(() => json(404, NOT_FOUND)), "/v1/event-rejections/search": respond((query) => json(200, rejectionPage(query.get("month") ?? "", []))) }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1", "--month", "2026-09"], deps), EXIT_OK);
  const result = stdoutJson() as { stored: boolean; billable: null; findings: Array<{ reason: string; searched_months: string[]; possible_causes: Array<{ cause: string }> }> };
  assert.equal(result.stored, false);
  assert.equal(result.billable, null);
  assert.equal(result.findings[0].reason, "no_record");
  assert.deepEqual(result.findings[0].searched_months, ["2026-09"]);
  assert.deepEqual(
    result.findings[0].possible_causes.map((cause) => cause.cause),
    ["rejected_without_record", "accepted_not_yet_stored", "not_delivered", "rejected_in_other_month", "rejected_before_recording"],
  );
  assert.deepEqual(
    requests(calls).map(([path, query]) => [path, query.month]),
    [
      ["/v1/events/lookup", undefined],
      ["/v1/event-rejections/search", "2026-09"],
    ],
  );
});

test("diagnose event는 --month를 여러 번 받아 최신 달부터 찾고, 기록 시작이 찾은 달보다 이르면 그 원인을 내지 않는다", async () => {
  const { deps, calls, stdoutJson } = await setup(
    server({ "/v1/events/lookup": respond(() => json(404, NOT_FOUND)), "/v1/event-rejections/search": respond((query) => json(200, rejectionPage(query.get("month") ?? "", []))) }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1", "--month", "2026-10", "--month", "2026-11", "--month", "2026-10"], deps), EXIT_OK);
  const result = stdoutJson() as { findings: Array<{ searched_months: string[]; possible_causes: Array<{ cause: string }> }> };
  assert.deepEqual(result.findings[0].searched_months, ["2026-11", "2026-10"]);
  assert.ok(!result.findings[0].possible_causes.some((cause) => cause.cause === "rejected_before_recording"));
  assert.deepEqual(
    requests(calls)
      .filter(([path]) => path === "/v1/event-rejections/search")
      .map(([, query]) => query.month),
    ["2026-11", "2026-10"],
  );
});

test("diagnose event는 거절 기록을 시작하기 전 달을 찾으면 그 시각을 원인으로 알린다", async () => {
  const { deps, stdoutJson } = await setup(
    server({
      "/v1/events/lookup": respond(() => json(404, NOT_FOUND)),
      "/v1/event-rejections/search": respond((query) => json(200, { ...rejectionPage(query.get("month") ?? "", []), recording_started_at: "2026-11-14T01:29:57Z" })),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1"], deps), EXIT_OK);
  const result = stdoutJson() as { findings: Array<{ possible_causes: Array<{ cause: string; hint: string }> }> };
  assert.deepEqual(result.findings[0].possible_causes.at(-1), {
    cause: "rejected_before_recording",
    hint: "서버는 2026-11-14T01:29:57Z부터 거절을 기록했습니다. 그 전에 거절된 요청은 기록에 없습니다",
  });
});

test("diagnose event는 255자를 넘는 transaction_id면 거절 기록을 찾지 않고 그 이유를 원인으로 낸다", async () => {
  const longId = "x".repeat(300);
  const { deps, calls, stdoutJson } = await setup(server({ "/v1/events/lookup": respond(() => json(404, NOT_FOUND)) }), { now: NOW });

  assert.equal(await run(["diagnose", "event", longId], deps), EXIT_OK);
  const result = stdoutJson() as { billable: null; findings: Array<{ possible_causes: Array<{ cause: string }> }> };
  assert.equal(result.billable, null);
  assert.deepEqual(
    result.findings[0].possible_causes.map((cause) => cause.cause),
    ["rejected_without_record", "accepted_not_yet_stored", "not_delivered", "rejected_in_other_month", "transaction_id_too_long"],
  );
  assert.equal(calls.length, 1);
});

test("diagnose event는 거절 기록 조회가 페이지 상한에 걸리면 notes에 적는다", async () => {
  const others = Array.from({ length: 100 }, () => rejection("tx-1-other", "validation_error"));
  const { deps, stdoutJson } = await setup(
    server({ "/v1/events/lookup": respond(() => json(404, NOT_FOUND)), "/v1/event-rejections/search": respond((query) => json(200, rejectionPage(query.get("month") ?? "", others, 5000))) }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "event", "tx-1", "--month", "2026-11"], deps), EXIT_OK);
  assert.deepEqual((stdoutJson() as { notes: string[] }).notes, ["거절 기록을 조회 상한까지만 확인했습니다"]);
});

test("diagnose event는 사용법이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  for (const argv of [
    ["diagnose", "event", "tx-1", "--month", "2026-13"],
    ["diagnose", "event", ""],
    ["diagnose", "event", "x".repeat(1025)],
    ["diagnose", "event"],
  ]) {
    const { deps, calls, stderrJson } = await setup([], { now: NOW });
    assert.equal(await run(argv, deps), EXIT_USAGE);
    assert.equal(calls.length, 0);
    assert.ok(["invalid_month", "invalid_transaction_id", "invalid_usage"].includes((stderrJson() as { code: string }).code));
  }
});

test("diagnose event는 조회 중 서버 오류를 그대로 올린다", async () => {
  const failing = await setup(server({ "/v1/events/lookup": EVENT, "/v1/invoices": respond(() => json(503, { code: "service_unavailable", message: "잠시 뒤" })) }), { now: NOW });
  assert.equal(await run(["diagnose", "event", "tx-1"], failing.deps), EXIT_RETRYABLE);
  assert.equal(failing.out.join(""), "");

  const unreachable = await setup([new TypeError("fetch failed")], { now: NOW });
  assert.equal(await run(["diagnose", "event", "tx-1"], unreachable.deps), EXIT_RETRYABLE);
  assert.equal(unreachable.out.join(""), "");

  const denied = await setup(server({ "/v1/events/lookup": respond(() => json(401, { code: "invalid_credentials", message: "인증 정보가 올바르지 않습니다" })) }), { now: NOW });
  assert.equal(await run(["diagnose", "event", "tx-1"], denied.deps), EXIT_FAILED);
  assert.deepEqual(denied.stderrJson(), { code: "invalid_credentials", message: "인증 정보가 올바르지 않습니다" });
});

const UNBILLED = {
  month: "2026-11",
  cutoff_at: "2026-11-14T12:00:00+09:00",
  unmatched_type: { event_count: 3, types: [{ type: "llm_requests", event_count: 3 }] },
  non_numeric_value: { event_count: 1, billable_metrics: [{ billable_metric_code: "input-tokens", event_count: 1 }] },
  future_timestamp: { event_count: 0, earliest_occurred_at: null, latest_occurred_at: null },
  negative_quantity: { event_count: 0, billable_metrics: [] },
};

const DRAFT = {
  month: "2026-11",
  calculated_at: "2026-11-14T12:00:00+09:00",
  auto_finalize_enabled: true,
  auto_finalize_at: "2026-12-02T04:00:00+09:00",
  auto_finalize_running: false,
  total_amount: 1000,
  customers: [
    { customer_id: CUSTOMER_ID, customer_name: "OpenAB", amount: 1000, lines: [], unpriced_usages: [], negative_usages: [], auto_finalize_held: true },
    { customer_id: "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", customer_name: "Beta", amount: 0, lines: [], unpriced_usages: [], negative_usages: [], auto_finalize_held: false },
  ],
};

function monthServer(overrides: Record<string, unknown> = {}) {
  return server({
    "/v1/unbilled-events": UNBILLED,
    "/v1/event-issues/search": issuePage([issue("tx-1", "unpriced_usage", "output-tokens"), issue("tx-2", "unpriced_usage", "output-tokens"), issue("tx-2", "unpriced_usage", "input-tokens")]),
    "/v1/event-rejections/search": respond((query) => {
      const counts: Record<string, number> = { customer_not_found: 2 };
      const code = query.get("error_code");
      return json(200, rejectionPage(query.get("month") ?? "", [], code === null ? 2 : (counts[code] ?? 0)));
    }),
    "/v1/events/types": { types: ["llm_request", "llm_requests", "api_call", "ping"] },
    "/v1/usage": USAGE,
    "/v1/invoices": { invoices: [] },
    "/v1/invoices/draft": DRAFT,
    ...overrides,
  });
}

test("diagnose month는 이번 달의 청구 제외, 거절, 미터 없는 유형, 자동 확정 일정을 한 JSON으로 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setup(monthServer(), { now: NOW });

  const code = await run(["diagnose", "month"], deps);

  assert.equal(code, EXIT_OK);
  assert.equal(err.join(""), "");
  assert.deepEqual(stdoutJson(), {
    month: "2026-11",
    counted_until: "2026-11-14T12:00:00+09:00",
    excluded_from_billing: {
      unknown_event_type: { event_count: 3, types: [{ type: "llm_requests", event_count: 3 }], complete: true },
      invalid_target_property: { event_count: 1, billable_metrics: [{ billable_metric_code: "input-tokens", event_count: 1 }], complete: true },
      unpriced_usage: {
        event_count: 2,
        billable_metrics: [
          { billable_metric_code: "input-tokens", event_count: 1 },
          { billable_metric_code: "output-tokens", event_count: 2 },
        ],
        complete: true,
      },
      received_after_cutoff: { event_count: 0, cutoff_at: "2026-12-02T00:00:00+09:00", complete: true },
      after_invoice_cutoff: { event_count: 0, customers: [], complete: true },
      negative_total: { combination_count: 0, combinations: [], complete: true },
    },
    not_yet_counted: { future_timestamp: UNBILLED.future_timestamp },
    counted_negative_events: UNBILLED.negative_quantity,
    rejected: { count: 2, codes: { customer_not_found: 2 }, recording_started_at: "2026-09-01T00:00:00Z" },
    types_without_meter: ["llm_requests", "ping"],
    suspected_typos: [{ event_type: "llm_requests", meter_event_type: "llm_request", billable_metric_codes: ["input-tokens", "output-tokens"] }],
    finalization: {
      usage_cutoff_at: "2026-12-02T00:00:00+09:00",
      auto_finalize_enabled: true,
      auto_finalize_at: "2026-12-02T04:00:00+09:00",
      auto_finalize_running: false,
      held_customers: [{ customer_id: CUSTOMER_ID, customer_name: "OpenAB" }],
    },
  });
  assert.deepEqual(requests(calls), [
    ["/v1/unbilled-events", { month: "2026-11" }],
    ["/v1/event-issues/search", { month: "2026-11", reason: "unpriced_usage", page: "0", size: "100" }],
    ["/v1/event-rejections/search", { month: "2026-11", page: "0", size: "1" }],
    ["/v1/event-rejections/search", { month: "2026-11", error_code: "customer_not_found", page: "0", size: "1" }],
    ["/v1/event-rejections/search", { month: "2026-11", error_code: "customer_archived", page: "0", size: "1" }],
    ["/v1/event-rejections/search", { month: "2026-11", error_code: "event_too_old", page: "0", size: "1" }],
    ["/v1/event-rejections/search", { month: "2026-11", error_code: "validation_error", page: "0", size: "1" }],
    ["/v1/events/types", { month: "2026-11" }],
    ["/v1/usage", { month: "2026-11" }],
    ["/v1/invoices", { month: "2026-11" }],
    ["/v1/invoices/draft", { month: "2026-11" }],
  ]);
});

test("diagnose month는 마감이 지난 달이면 마감 뒤에 받은 이벤트를 받은 시각 최신순으로 넘겨 센다", async () => {
  const late = (index: number) => ({ ...EVENT, transaction_id: `late-${index}`, occurred_at: "2026-10-31T20:00:00+09:00", received_at: "2026-11-02T10:00:00+09:00" });
  const onTime = { ...EVENT, transaction_id: "on-time", occurred_at: "2026-10-31T20:00:00+09:00", received_at: "2026-11-01T23:59:59+09:00" };
  const { deps, calls, stdoutJson } = await setup(
    monthServer({
      "/v1/event-rejections/search": respond((query) => json(200, rejectionPage(query.get("month") ?? "", [], 0))),
      "/v1/events": respond((query) =>
        json(200, {
          month: "2026-10",
          page: Number(query.get("page")),
          size: 100,
          total: 150,
          events: query.get("page") === "0" ? Array.from({ length: 100 }, (_, index) => late(index)) : [late(100), onTime],
        }),
      ),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "month", "--month", "2026-10"], deps), EXIT_OK);
  const result = stdoutJson() as { excluded_from_billing: { received_after_cutoff: unknown }; rejected: unknown };
  assert.deepEqual(result.excluded_from_billing.received_after_cutoff, { event_count: 101, cutoff_at: "2026-11-02T00:00:00+09:00", complete: true });
  assert.deepEqual(result.rejected, { count: 0, codes: {}, recording_started_at: "2026-09-01T00:00:00Z" });
  const eventCalls = requests(calls).filter(([path]) => path === "/v1/events");
  assert.deepEqual(
    eventCalls.map(([, query]) => query),
    [
      { month: "2026-10", page: "0", size: "100", sort: "received_at", order: "desc" },
      { month: "2026-10", page: "1", size: "100", sort: "received_at", order: "desc" },
    ],
  );
  assert.equal(requests(calls).filter(([path]) => path === "/v1/event-rejections/search").length, 1);
});

test("diagnose month는 서버가 유형 목록을 잘라 줬으면 complete를 false로, 알 수 없는 거절 코드는 other로 낸다", async () => {
  const truncated = { ...UNBILLED, unmatched_type: { event_count: 30, types: [{ type: "llm_requests", event_count: 3 }] } };
  const { deps, stdoutJson } = await setup(
    monthServer({
      "/v1/unbilled-events": truncated,
      "/v1/event-rejections/search": respond((query) => json(200, rejectionPage("2026-11", [], query.get("error_code") === null ? 5 : query.get("error_code") === "event_too_old" ? 1 : 0))),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "month"], deps), EXIT_OK);
  const result = stdoutJson() as { excluded_from_billing: { unknown_event_type: { complete: boolean } }; rejected: { codes: unknown } };
  assert.equal(result.excluded_from_billing.unknown_event_type.complete, false);
  assert.deepEqual(result.rejected.codes, { event_too_old: 1, other: 4 });
});

test("diagnose month는 사용법이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([], { now: NOW });

  assert.equal(await run(["diagnose", "month", "--month", "2026-1"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_month");
});

test("diagnose month는 조회 중 서버 오류를 그대로 올린다", async () => {
  const { deps, out } = await setup(monthServer({ "/v1/invoices/draft": respond(() => json(500, { code: "internal_error", message: "서버 오류" })) }), { now: NOW });

  assert.equal(await run(["diagnose", "month"], deps), EXIT_RETRYABLE);
  assert.equal(out.join(""), "");
});

test("diagnose month는 미터 없는 유형과 오타 후보를 서버가 그 달에 집계하는 미터로 가린다", async () => {
  const { deps, stdoutJson } = await setup(
    monthServer({
      "/v1/events/types": { types: ["rv_rr3", "rv_rr4", "llm_request"] },
      "/v1/usage": { month: "2026-11", billable_metric_usages: [usage("rv-rr3", "rv_rr3"), usage("input-tokens", "llm_request")] },
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "month"], deps), EXIT_OK);
  const result = stdoutJson() as { types_without_meter: string[]; suspected_typos: unknown[] };
  assert.deepEqual(result.types_without_meter, ["rv_rr4"]);
  assert.deepEqual(result.suspected_typos, [{ event_type: "rv_rr4", meter_event_type: "rv_rr3", billable_metric_codes: ["rv-rr3"] }]);
});

test("diagnose month는 마감 전에 확정한 고객의 확정 뒤 이벤트와 음수 합계 조합을 청구 제외로 낸다", async () => {
  const early = { ...INVOICE, month: "2026-11", cutoff_at: "2026-11-10T10:00:00+09:00" };
  const auto = { ...INVOICE, id: "1f2e3d4c-5b6a-4987-8a6b-5c4d3e2f1a0b", customer_id: "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", month: "2026-11", cutoff_at: "2026-12-02T00:00:00+09:00" };
  const event = (id: string, receivedAt: string, occurredAt = "2026-11-09T10:00:00+09:00") => ({ ...EVENT, transaction_id: id, occurred_at: occurredAt, received_at: receivedAt });
  const negative = { billable_metric_code: "input-tokens", target_property: "input-tokens", dimension_values: { model: "ab-1" }, quantity: -5 };
  const { deps, calls, stdoutJson } = await setup(
    monthServer({
      "/v1/invoices": { invoices: [early, auto] },
      "/v1/events": respond(() =>
        json(200, {
          month: "2026-11",
          page: 0,
          size: 100,
          total: 4,
          events: [
            event("after-1", "2026-11-12T10:00:00+09:00"),
            event("after-2", "2026-11-10T10:00:00+09:00"),
            event("future", "2026-11-09T10:00:00+09:00", "2026-11-11T10:00:00+09:00"),
            event("before", "2026-11-09T09:00:00+09:00"),
          ],
        }),
      ),
      "/v1/invoices/draft": { ...DRAFT, customers: [{ ...DRAFT.customers[1], negative_usages: [negative] }] },
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "month"], deps), EXIT_OK);
  const result = stdoutJson() as { excluded_from_billing: { after_invoice_cutoff: unknown; negative_total: unknown } };
  assert.deepEqual(result.excluded_from_billing.after_invoice_cutoff, {
    event_count: 3,
    customers: [{ customer_id: CUSTOMER_ID, customer_name: "OpenAB", invoice_id: INVOICE.id, cutoff_at: early.cutoff_at, event_count: 3 }],
    complete: true,
  });
  assert.deepEqual(result.excluded_from_billing.negative_total, {
    combination_count: 1,
    combinations: [{ customer_id: DRAFT.customers[1].customer_id, customer_name: "Beta", billable_metric_code: "input-tokens", dimension_values: { model: "ab-1" }, quantity: -5 }],
    complete: true,
  });
  assert.deepEqual(
    requests(calls).filter(([path]) => path === "/v1/events"),
    [["/v1/events", { month: "2026-11", customer_id: CUSTOMER_ID, page: "0", size: "100", sort: "received_at", order: "desc" }]],
  );
});

test("diagnose month는 단가 없는 사용량과 마감 뒤 수신을 페이지 상한까지만 세면 complete를 false로 낸다", async () => {
  const unpriced = Array.from({ length: 100 }, (_, index) => issue(`tx-${index}`, "unpriced_usage", "output-tokens"));
  const late = Array.from({ length: 100 }, (_, index) => ({ ...EVENT, transaction_id: `late-${index}`, occurred_at: "2026-10-31T20:00:00+09:00", received_at: "2026-11-02T10:00:00+09:00" }));
  const { deps, stdoutJson } = await setup(
    monthServer({
      "/v1/event-issues/search": respond((query) => json(200, issuePage(unpriced, 5000, Number(query.get("page"))))),
      "/v1/events": respond((query) => json(200, { month: "2026-10", page: Number(query.get("page")), size: 100, total: 5000, events: late })),
    }),
    { now: NOW },
  );

  assert.equal(await run(["diagnose", "month", "--month", "2026-10"], deps), EXIT_OK);
  const result = stdoutJson() as { excluded_from_billing: { unpriced_usage: { complete: boolean }; received_after_cutoff: { event_count: number; complete: boolean } } };
  assert.equal(result.excluded_from_billing.unpriced_usage.complete, false);
  assert.equal(result.excluded_from_billing.received_after_cutoff.complete, false);
  assert.equal(result.excluded_from_billing.received_after_cutoff.event_count, 1000);
});

test("오타 판정은 대소문자와 구분 기호 차이를 무시하고, 6자 미만 유형은 한 글자 차이까지만 본다", () => {
  const cases: Array<[string, string, boolean]> = [
    ["LLM-Request", "llm_request", true],
    ["llm.request", "llm_request", true],
    ["llm_requests", "llm_request", true],
    ["llm_reqest", "llm_request", true],
    ["llm_rqest", "llm_request", true],
    ["llm_rqst", "llm_request", false],
    ["apx", "api", true],
    ["axx", "api", false],
    ["ping", "api_call", false],
  ];
  for (const [type, meterType, isTypo] of cases) assert.equal(looksLikeTypo(type, meterType), isTypo, `${type} 대 ${meterType}`);
});
