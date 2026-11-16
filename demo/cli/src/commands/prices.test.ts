import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { TEST_KEY, json, setup } from "../testing.ts";

const CUSTOMER_ID = "008cd6a7-6ff9-505d-9421-747e7d2d62aa";
const PRICES = {
  billable_metric_prices: [
    {
      billable_metric_code: "input-tokens",
      dimension_properties: ["model"],
      price_rates: [
        {
          dimension_values: { model: "claude-fable-5" },
          unit_price: 0.0159,
          id: "95fec38f-a3e3-4a30-b73b-07a5da2445a4",
          customer_id: null,
          effective_from: "2000-01",
          effective_until: null,
          model: "per_unit",
          tiers: null,
          archived_at: null,
          archived_from: null,
        },
      ],
    },
  ],
};

test("prices list는 월과 고객을 쿼리로 보내고 서버 응답을 그대로 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setup([json(200, PRICES)]);

  const code = await run(["prices", "list", "--month", "2026-11", "--customer", CUSTOMER_ID], deps);

  assert.equal(code, EXIT_OK);
  assert.deepEqual(stdoutJson(), PRICES);
  assert.equal(err.join(""), "");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, "GET");
  assert.equal(calls[0].url, `http://localhost:8080/v1/billable-metric-prices?month=2026-11&customer_id=${CUSTOMER_ID}`);
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("prices list에 옵션이 없으면 쿼리 없이 보내 서버가 이번 달 공통 단가를 고른다", async () => {
  const { deps, calls } = await setup([json(200, { billable_metric_prices: [] })]);

  assert.equal(await run(["prices", "list"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/billable-metric-prices");
});

test("prices list의 월이나 고객 형식이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  for (const [args, expected] of [
    [["--month", "2026-00"], "invalid_month"],
    [["--customer", "acme"], "invalid_customer_id"],
  ] as const) {
    const { deps, calls, out, stderrJson } = await setup([]);
    assert.equal(await run(["prices", "list", ...args], deps), EXIT_USAGE);
    assert.equal(calls.length, 0);
    assert.equal(out.join(""), "");
    assert.equal((stderrJson() as { code: string }).code, expected);
  }
});

test("prices list에서 서버가 고객을 못 찾으면 종료 코드 1과 서버 오류를 낸다", async () => {
  const notFound = { code: "customer_not_found", message: "고객을 찾을 수 없습니다" };
  const { deps, stderrJson } = await setup([json(404, notFound)]);

  assert.equal(await run(["prices", "list", "--customer", CUSTOMER_ID], deps), EXIT_FAILED);
  assert.deepEqual(stderrJson(), notFound);
});

test("prices list에서 서버가 5xx면 종료 코드 3이다", async () => {
  const { deps } = await setup([json(503, { code: "service_unavailable", message: "잠시 뒤" })]);

  assert.equal(await run(["prices", "list"], deps), EXIT_RETRYABLE);
});
