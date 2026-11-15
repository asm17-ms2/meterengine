import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { TEST_KEY, json, setup } from "../testing.ts";

const METER = {
  code: "input-tokens",
  name: "입력 토큰",
  event_type: "llm_request",
  aggregation: "sum",
  target_property: "input_tokens",
  dimension_properties: ["model"],
  archived_at: null,
  active_from: "0001-01",
  inactive_from: null,
  unit_size: 1,
  rounding_mode: "none",
  rounding_precision: 0,
  percentile: null,
  version: 0,
};

test("meters list는 서버 응답 JSON을 그대로 낸다", async () => {
  const body = { billable_metrics: [METER] };
  const { deps, calls, stdoutJson } = await setup([json(200, body)]);

  assert.equal(await run(["meters", "list"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), body);
  assert.equal(calls[0].url, "http://localhost:8080/v1/billable-metrics");
  assert.equal(calls[0].method, "GET");
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("meters list는 보관 상태를 쿼리로 보낸다", async () => {
  const { deps, calls } = await setup([json(200, { billable_metrics: [] })]);

  assert.equal(await run(["meters", "list", "--status", "archived"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/billable-metrics?status=archived");
});

test("meters list에 모르는 보관 상태를 주면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["meters", "list", "--status", "deleted"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_status");
});

test("meters get은 code로 조회해 서버 응답 JSON을 그대로 낸다", async () => {
  const { deps, calls, stdoutJson } = await setup([json(200, METER)]);

  assert.equal(await run(["meters", "get", "input-tokens"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), METER);
  assert.equal(calls[0].url, "http://localhost:8080/v1/billable-metrics/input-tokens");
});

test("meters get은 code를 경로에 맞게 인코딩한다", async () => {
  const { deps, calls } = await setup([json(200, METER)]);

  assert.equal(await run(["meters", "get", "a/b c"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/billable-metrics/a%2Fb%20c");
});

test("없는 미터면 종료 코드 1과 서버의 오류 JSON을 낸다", async () => {
  const notFound = { code: "billable_metric_not_found", message: "미터를 찾을 수 없습니다" };
  const { deps, out, stderrJson } = await setup([json(404, notFound)]);

  assert.equal(await run(["meters", "get", "nope"], deps), EXIT_FAILED);
  assert.equal(out.join(""), "");
  assert.deepEqual(stderrJson(), notFound);
});

test("meters get에 빈 code를 주면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls } = await setup([]);

  assert.equal(await run(["meters", "get", ""], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
});
