import assert from "node:assert/strict";
import { test } from "node:test";
import { saveApiKey } from "../credentials.ts";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { LIVE_KEY, TEST_KEY, json, setup } from "../testing.ts";
import type { Reply } from "../testing.ts";

const OTHER_TEST_KEY = "me_test_AAAAbbbbCCCCddddEEEEffffGGGGhhhh";

function metric(code: string, overrides: Record<string, unknown> = {}) {
  return {
    code,
    name: code,
    event_type: "api_call",
    aggregation: "sum",
    target_property: "units",
    dimension_properties: [],
    archived_at: null,
    active_from: "0001-01",
    inactive_from: null,
    unit_size: 1,
    rounding_mode: "none",
    rounding_precision: 0,
    percentile: null,
    version: 0,
    ...overrides,
  };
}

function rate(overrides: Record<string, unknown> = {}) {
  return {
    dimension_values: {},
    unit_price: 50,
    id: crypto.randomUUID(),
    customer_id: null,
    effective_from: "2026-10",
    effective_until: null,
    model: "per_unit",
    tiers: null,
    archived_at: null,
    archived_from: null,
    ...overrides,
  };
}

function metrics(...items: unknown[]) {
  return json(200, { billable_metrics: items });
}

function prices(...items: { code: string; dimensions?: string[]; rates: unknown[] }[]) {
  return json(200, { billable_metric_prices: items.map((item) => ({ billable_metric_code: item.code, dimension_properties: item.dimensions ?? [], price_rates: item.rates })) });
}

// 서버는 키가 보는 업무 환경을 응답 헤더로 알린다.
function on(environment: "legacy" | "test" | "live", ...responses: Response[]): Response[] {
  for (const response of responses) response.headers.set("X-MeterEngine-Environment", environment);
  return responses;
}

async function setupProfiles(replies: Reply[], keys: Record<string, string> = { test: TEST_KEY, live: LIVE_KEY }) {
  const context = await setup(replies, { env: {} });
  for (const [profile, key] of Object.entries(keys)) await saveApiKey(context.deps.configDir, key, profile);
  return context;
}

test("diff는 두 프로필의 키로 과금 미터와 이번 달 단가를 받아 같으면 identical true를 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setupProfiles([
    ...on("test", metrics(metric("api-calls")), prices({ code: "api-calls", rates: [rate()] })),
    ...on("live", metrics(metric("api-calls", { version: 3 })), prices({ code: "api-calls", rates: [rate({ effective_from: "2026-11" })] })),
  ]);

  const code = await run(["diff", "test", "live"], deps);

  assert.equal(code, EXIT_OK);
  assert.equal(err.join(""), "");
  assert.deepEqual(
    calls.map((call) => [call.url, call.headers.get("Authorization")]),
    [
      ["http://localhost:8080/v1/billable-metrics?status=all", `Bearer ${TEST_KEY}`],
      ["http://localhost:8080/v1/billable-metric-prices?month=2026-11", `Bearer ${TEST_KEY}`],
      ["http://localhost:8080/v1/billable-metrics?status=all", `Bearer ${LIVE_KEY}`],
      ["http://localhost:8080/v1/billable-metric-prices?month=2026-11", `Bearer ${LIVE_KEY}`],
    ],
  );
  assert.deepEqual(stdoutJson(), {
    identical: true,
    month: "2026-11",
    left: { profile: "test", mode: "test" },
    right: { profile: "live", mode: "live" },
    warnings: [],
    billable_metrics: [],
    prices: [],
  });
});

test("diff는 계산 칸이 다른 과금 미터와 한쪽에만 있는 과금 미터를 칸 단위로 보여 준다", async () => {
  const { deps, stdoutJson } = await setupProfiles([
    metrics(metric("api-calls", { name: "API 호출" }), metric("seats", { aggregation: "max", dimension_properties: ["plan"] }), metric("storage")),
    prices(),
    metrics(metric("api-calls", { name: "호출 수", target_property: "unit", unit_size: 1000, rounding_mode: "ceil" }), metric("seats", { aggregation: "min", dimension_properties: ["plan"], archived_at: "2026-11-01T00:00:00Z" })),
    prices(),
  ]);

  assert.equal(await run(["diff", "test", "live"], deps), EXIT_OK);

  const output = stdoutJson() as { identical: boolean; billable_metrics: unknown[] };
  assert.equal(output.identical, false);
  assert.deepEqual(output.billable_metrics, [
    { code: "api-calls", field: "target_property", left: "units", right: "unit" },
    { code: "api-calls", field: "unit_size", left: 1, right: 1000 },
    { code: "api-calls", field: "rounding_mode", left: "none", right: "ceil" },
    { code: "seats", field: "aggregation", left: "max", right: "min" },
    { code: "seats", field: "archived", left: false, right: true },
    {
      code: "storage",
      field: null,
      left: { event_type: "api_call", aggregation: "sum", target_property: "units", dimension_properties: [], unit_size: 1, rounding_mode: "none", rounding_precision: 0, percentile: null, archived: false },
      right: null,
    },
  ]);
});

test("diff는 단가를 과금 미터 code와 속성 값 조합으로 짝지어 모델, 단가, 구간이 다른 것만 보여 준다", async () => {
  const tiers = [{ up_to: 100, unit_price: 10 }, { up_to: null, unit_price: 5 }];
  const { deps, stdoutJson } = await setupProfiles([
    metrics(),
    prices(
      { code: "input-tokens", dimensions: ["model", "region"], rates: [rate({ dimension_values: { model: "a", region: "kr" }, unit_price: 3 }), rate({ dimension_values: { model: "b", region: "kr" }, unit_price: 4 })] },
      { code: "requests", rates: [rate({ model: "graduated", unit_price: null, tiers })] },
      { code: "storage", rates: [rate({ unit_price: 50 })] },
    ),
    metrics(),
    prices(
      { code: "input-tokens", dimensions: ["model", "region"], rates: [rate({ dimension_values: { region: "kr", model: "a" }, unit_price: 3 }), rate({ dimension_values: { model: "b", region: "kr" }, unit_price: 5 })] },
      { code: "requests", rates: [rate({ model: "graduated", unit_price: null, tiers: [{ unit_price: 10, up_to: 100 }, { unit_price: 5, up_to: null }] })] },
      { code: "storage", rates: [] },
      { code: "support", rates: [rate({ unit_price: 1000 })] },
    ),
  ]);

  assert.equal(await run(["diff", "test", "live"], deps), EXIT_OK);

  const output = stdoutJson() as { identical: boolean; billable_metrics: unknown[]; prices: unknown[] };
  assert.equal(output.identical, false);
  assert.deepEqual(output.billable_metrics, []);
  assert.deepEqual(output.prices, [
    { billable_metric_code: "input-tokens", dimension_values: { model: "b", region: "kr" }, left: { model: "per_unit", unit_price: 4, tiers: null }, right: { model: "per_unit", unit_price: 5, tiers: null } },
    { billable_metric_code: "storage", dimension_values: {}, left: { model: "per_unit", unit_price: 50, tiers: null }, right: null },
    { billable_metric_code: "support", dimension_values: {}, left: null, right: { model: "per_unit", unit_price: 1000, tiers: null } },
  ]);
});

test("diff --month는 그 월의 단가를 두 환경에 같은 월로 묻는다", async () => {
  const { deps, calls } = await setupProfiles([metrics(), prices(), metrics(), prices()]);

  assert.equal(await run(["diff", "test", "live", "--month", "2026-12"], deps), EXIT_OK);

  assert.equal(calls[1].url, "http://localhost:8080/v1/billable-metric-prices?month=2026-12");
  assert.equal(calls[3].url, "http://localhost:8080/v1/billable-metric-prices?month=2026-12");
});

test("diff는 이번 달을 KST로 정한다", async () => {
  const { deps, calls, stdoutJson } = await setupProfiles([metrics(), prices(), metrics(), prices()]);
  deps.now = () => new Date("2026-11-30T15:00:00Z");

  assert.equal(await run(["diff", "test", "live"], deps), EXIT_OK);

  assert.equal(calls[1].url, "http://localhost:8080/v1/billable-metric-prices?month=2026-12");
  assert.equal((stdoutJson() as { month: string }).month, "2026-12");
});

test("두 프로필이 같은 키면 same_api_key 경고를 넣고 비교는 끝까지 한다", async () => {
  const { deps, stdoutJson } = await setupProfiles([...on("test", metrics(metric("a")), prices()), ...on("test", metrics(metric("a")), prices())], { test: TEST_KEY, copy: TEST_KEY });

  assert.equal(await run(["diff", "test", "copy"], deps), EXIT_OK);

  const output = stdoutJson() as { identical: boolean; warnings: { code: string }[]; right: unknown };
  assert.equal(output.identical, true);
  assert.deepEqual(output.right, { profile: "copy", mode: "test" });
  assert.deepEqual(output.warnings.map((warning) => warning.code), ["same_api_key"]);
});

test("서버가 알린 업무 환경이 같으면 same_environment 경고를 넣는다", async () => {
  const { deps, stdoutJson } = await setupProfiles([...on("test", metrics(), prices()), ...on("test", metrics(), prices())], { test: TEST_KEY, other: OTHER_TEST_KEY });

  assert.equal(await run(["diff", "test", "other"], deps), EXIT_OK);

  const output = stdoutJson() as { warnings: { code: string; message: string }[] };
  assert.deepEqual(output.warnings.map((warning) => warning.code), ["same_environment"]);
  assert.match(output.warnings[0].message, /test 업무 환경/);
});

test("접두사가 test와 live여도 두 키가 legacy 범위면 mode를 legacy로 적고 same_environment 경고를 넣는다", async () => {
  const { deps, stdoutJson } = await setupProfiles([...on("legacy", metrics(metric("a")), prices()), ...on("legacy", metrics(metric("a")), prices())]);

  assert.equal(await run(["diff", "test", "live"], deps), EXIT_OK);

  const output = stdoutJson() as { identical: boolean; left: unknown; right: unknown; warnings: { code: string; message: string }[] };
  assert.equal(output.identical, true);
  assert.deepEqual(output.left, { profile: "test", mode: "legacy" });
  assert.deepEqual(output.right, { profile: "live", mode: "legacy" });
  assert.deepEqual(output.warnings.map((warning) => warning.code), ["same_environment"]);
  assert.match(output.warnings[0].message, /legacy 범위/);
});

test("한쪽만 legacy면 업무 환경이 달라 경고하지 않는다", async () => {
  const { deps, stdoutJson } = await setupProfiles([...on("legacy", metrics(), prices()), ...on("live", metrics(), prices())]);

  assert.equal(await run(["diff", "test", "live"], deps), EXIT_OK);

  const output = stdoutJson() as { left: unknown; right: unknown; warnings: unknown[] };
  assert.deepEqual(output.left, { profile: "test", mode: "legacy" });
  assert.deepEqual(output.right, { profile: "live", mode: "live" });
  assert.deepEqual(output.warnings, []);
});

test("diff 도움말은 판정과 관계없이 종료 코드 0이라고 알리고 부르는 API를 모두 적는다", async () => {
  const { deps, out } = await setupProfiles([]);

  assert.equal(await run(["diff", "--help"], deps), EXIT_OK);

  const help = out.join("");
  assert.match(help, /종료 코드 0/);
  assert.match(help, /GET \/v1\/customers/);
});

test("diff --external-id는 두 환경에서 외부 ID로 고객을 찾아 고객별 단가도 비교한다", async () => {
  const { deps, calls, stdoutJson } = await setupProfiles([
    metrics(),
    prices(),
    json(200, { customers: [{ id: "c-test", external_id: "acme", name: "Acme", billing_email: null, created_at: null }] }),
    prices({ code: "api-calls", rates: [rate({ customer_id: "c-test", unit_price: 30 })] }),
    metrics(),
    prices(),
    json(200, { customers: [{ id: "c-live", external_id: "acme", name: "Acme", billing_email: null, created_at: null }] }),
    prices({ code: "api-calls", rates: [rate({ customer_id: "c-live", unit_price: 40 })] }),
  ]);

  assert.equal(await run(["diff", "test", "live", "--external-id", "acme"], deps), EXIT_OK);

  assert.equal(calls[2].url, "http://localhost:8080/v1/customers?external_id=acme");
  assert.equal(calls[3].url, "http://localhost:8080/v1/billable-metric-prices?month=2026-11&customer_id=c-test");
  assert.equal(calls[7].url, "http://localhost:8080/v1/billable-metric-prices?month=2026-11&customer_id=c-live");
  const output = stdoutJson() as Record<string, unknown>;
  assert.equal(output.identical, false);
  assert.deepEqual(output.prices, []);
  assert.deepEqual(output.customer, { external_id: "acme", left: "c-test", right: "c-live" });
  assert.deepEqual(output.customer_prices, [
    { billable_metric_code: "api-calls", dimension_values: {}, left: { model: "per_unit", unit_price: 30, tiers: null }, right: { model: "per_unit", unit_price: 40, tiers: null } },
  ]);
});

test("외부 ID 고객이 한쪽에만 있으면 고객별 단가는 비교하지 않고 경고와 identical false를 낸다", async () => {
  const { deps, calls, stdoutJson } = await setupProfiles([
    metrics(),
    prices(),
    json(200, { customers: [{ id: "c-test", external_id: "acme", name: "Acme", billing_email: null, created_at: null }] }),
    prices(),
    metrics(),
    prices(),
    json(200, { customers: [] }),
  ]);

  assert.equal(await run(["diff", "test", "live", "--external-id", "acme"], deps), EXIT_OK);

  assert.equal(calls.length, 7);
  const output = stdoutJson() as Record<string, unknown>;
  assert.equal(output.identical, false);
  assert.deepEqual(output.customer, { external_id: "acme", left: "c-test", right: null });
  assert.equal(output.customer_prices, null);
  assert.deepEqual(output.warnings, [{ code: "customer_not_found", message: "고객별 단가를 비교하지 않았습니다. 외부 ID acme 고객이 없는 프로필: live" }]);
});

test("외부 ID 고객이 두 환경 모두에 없으면 과금 미터와 단가가 같을 때 identical true에 경고만 낸다", async () => {
  const { deps, stdoutJson } = await setupProfiles([metrics(), prices(), json(200, { customers: [] }), metrics(), prices(), json(200, { customers: [] })]);

  assert.equal(await run(["diff", "test", "live", "--external-id", "acme"], deps), EXIT_OK);

  const output = stdoutJson() as { identical: boolean; warnings: { message: string }[] };
  assert.equal(output.identical, true);
  assert.match(output.warnings[0].message, /없는 프로필: test, live$/);
});

test("사용법이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  const cases: [string[], string][] = [
    [["diff", "test", "nope"], "missing_profile"],
    [["diff", "test", "bad name"], "invalid_profile_name"],
    [["diff", "test", "live", "--month", "2026-13"], "invalid_month"],
    [["diff", "test", "live", "--external-id", ""], "invalid_external_id"],
    [["diff", "test"], "invalid_usage"],
    [["--profile", "nope", "diff", "test", "live"], "invalid_usage"],
    [["diff", "test", "live", "--profile", "live"], "invalid_usage"],
  ];
  for (const [argv, errorCode] of cases) {
    const { deps, calls, out, stderrJson } = await setupProfiles([]);
    assert.equal(await run(argv, deps), EXIT_USAGE, argv.join(" "));
    assert.equal(calls.length, 0);
    assert.equal(out.join(""), "");
    assert.equal((stderrJson() as { code: string }).code, errorCode);
  }
});

test("한쪽 서버가 거절하면 그 오류로 종료 코드 1, 다시 하면 될 수 있는 실패는 3이다", async () => {
  const rejected = await setupProfiles([metrics(), prices(), json(401, { code: "invalid_credentials", message: "인증 정보가 올바르지 않습니다" })]);
  assert.equal(await run(["diff", "test", "live"], rejected.deps), EXIT_FAILED);
  assert.equal(rejected.out.join(""), "");
  assert.deepEqual(rejected.stderrJson(), { code: "invalid_credentials", message: "인증 정보가 올바르지 않습니다" });

  const unavailable = await setupProfiles([metrics(), json(503, { code: "service_unavailable", message: "잠시 뒤" })]);
  assert.equal(await run(["diff", "test", "live"], unavailable.deps), EXIT_RETRYABLE);
});
