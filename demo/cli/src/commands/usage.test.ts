import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { json, setup } from "../testing.ts";

const CUSTOMER_ID = "a728e7b6-d82b-4f3c-a960-a66a02794c1d";
const USAGE = {
  month: "2026-11",
  billable_metric_usages: [
    {
      code: "input-tokens",
      name: "입력 토큰",
      event_type: "llm_request",
      aggregation: "sum",
      target_property: "input_tokens",
      customers: [{ customer_id: CUSTOMER_ID, customer_name: "아크메 주식회사", quantity: 1200, dimension_usages: [], raw_quantity: 1200, raw_quantity_numerator: 1200, raw_quantity_denominator: 1 }],
    },
  ],
};
const SEARCH_PAGE = {
  items: [{ customer_id: CUSTOMER_ID, customer_name: "아크메 주식회사", metrics: USAGE.billable_metric_usages }],
  total: 1,
  total_unfiltered: 5,
  page: 1,
  size: 200,
  summary: { month: "2026-11", customer_count: 1, metric_count: 1, line_count: 1, nonzero_customer_count: 1 },
};

function requestOf(url: string): { path: string; query: Record<string, string> } {
  const parsed = new URL(url);
  return { path: parsed.pathname, query: Object.fromEntries(parsed.searchParams) };
}

test("usage는 좁히지 않으면 GET /v1/usage의 응답을 그대로 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setup([json(200, USAGE)]);

  assert.equal(await run(["usage"], deps), EXIT_OK);
  assert.equal(calls.length, 1);
  assert.deepEqual(requestOf(calls[0].url), { path: "/v1/usage", query: {} });
  assert.deepEqual(stdoutJson(), USAGE);
  assert.equal(err.join(""), "");
});

test("usage --month는 그 달을 쿼리로 보낸다", async () => {
  const { deps, calls } = await setup([json(200, { ...USAGE, month: "2026-10" })]);

  assert.equal(await run(["usage", "--month", "2026-10"], deps), EXIT_OK);
  assert.deepEqual(requestOf(calls[0].url), { path: "/v1/usage", query: { month: "2026-10" } });
});

test("usage --customer는 검색 API에 고객 UUID와 최대 쪽 크기를 보내고 응답을 그대로 낸다", async () => {
  const { deps, calls, stdoutJson } = await setup([json(200, SEARCH_PAGE)]);

  assert.equal(await run(["usage", "--customer", CUSTOMER_ID], deps), EXIT_OK);
  assert.deepEqual(requestOf(calls[0].url), { path: "/v1/usage/search", query: { customer_id: CUSTOMER_ID, size: "200" } });
  assert.deepEqual(stdoutJson(), SEARCH_PAGE);
});

test("usage --meter와 --month는 검색 API에 미터 코드와 달을 함께 보낸다", async () => {
  const { deps, calls } = await setup([json(200, SEARCH_PAGE)]);

  assert.equal(await run(["usage", "--meter", "input-tokens", "--month", "2026-11"], deps), EXIT_OK);
  assert.deepEqual(requestOf(calls[0].url), { path: "/v1/usage/search", query: { month: "2026-11", billable_metric_code: "input-tokens", size: "200" } });
});

test("usage는 고객과 미터를 함께 좁히고 --page와 --size를 그대로 보낸다", async () => {
  const { deps, calls } = await setup([json(200, { ...SEARCH_PAGE, page: 2, size: 50 })]);

  assert.equal(await run(["usage", "--customer", CUSTOMER_ID, "--meter", "input-tokens", "--page", "2", "--size", "50"], deps), EXIT_OK);
  assert.deepEqual(requestOf(calls[0].url).query, { customer_id: CUSTOMER_ID, billable_metric_code: "input-tokens", page: "2", size: "50" });
});

test("usage --customer에 외부 ID를 넣으면 서버에 보내지 않고 종료 코드 2와 UUID 찾는 길을 낸다", async () => {
  const { deps, calls, out, stderrJson } = await setup([]);

  assert.equal(await run(["usage", "--customer", "c-1"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal(out.join(""), "");
  const error = stderrJson() as { code: string; message: string };
  assert.equal(error.code, "invalid_customer_id");
  assert.match(error.message, /customers list --external-id/);
});

test("usage는 형식이 틀린 달이면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["usage", "--month", "2026-13"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_month");
});

test("usage는 빈 미터 코드면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["usage", "--meter", " "], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_meter");
});

test("usage는 좁히지 않고 --page나 --size를 쓰면 서버에 보내지 않고 종료 코드 2다", async () => {
  for (const args of [["--page", "2"], ["--size", "10"]]) {
    const { deps, calls, stderrJson } = await setup([]);

    assert.equal(await run(["usage", ...args], deps), EXIT_USAGE);
    assert.equal(calls.length, 0);
    assert.equal((stderrJson() as { code: string }).code, "page_requires_filter");
  }
});

test("usage는 범위 밖의 --page와 --size를 서버에 보내지 않고 종료 코드 2다", async () => {
  const cases: [string[], string][] = [
    [["--page", "0"], "invalid_page"],
    [["--page", "abc"], "invalid_page"],
    [["--page", "2147483648"], "invalid_page"],
    [["--size", "0"], "invalid_size"],
    [["--size", "201"], "invalid_size"],
    [["--size", "1.5"], "invalid_size"],
  ];
  for (const [args, code] of cases) {
    const { deps, calls, stderrJson } = await setup([]);

    assert.equal(await run(["usage", "--meter", "input-tokens", ...args], deps), EXIT_USAGE);
    assert.equal(calls.length, 0);
    assert.equal((stderrJson() as { code: string }).code, code);
  }
});

test("usage는 조직에 없는 고객이면 서버의 404 오류를 표준 오류에 내고 종료 코드 1이다", async () => {
  const notFound = { code: "customer_not_found", message: "고객을 찾을 수 없습니다" };
  const { deps, out, stderrJson } = await setup([json(404, notFound)]);

  assert.equal(await run(["usage", "--customer", CUSTOMER_ID], deps), EXIT_FAILED);
  assert.equal(out.join(""), "");
  assert.deepEqual(stderrJson(), notFound);
});

test("usage는 서버가 5xx면 종료 코드 3이다", async () => {
  const { deps } = await setup([json(503, { code: "service_unavailable", message: "잠시 뒤에 다시 시도하세요" })]);

  assert.equal(await run(["usage", "--meter", "input-tokens"], deps), EXIT_RETRYABLE);
});
