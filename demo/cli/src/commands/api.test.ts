import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { TEST_KEY, json, setup } from "../testing.ts";

const ORGANIZATION = { id: "d7cee55d-8c82-4afc-b996-6749d8b26a4e", name: "데모 조직", status: "active" };

test("api get은 경로로 GET을 보내고 응답 JSON을 그대로 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setup([json(200, ORGANIZATION)]);

  assert.equal(await run(["api", "get", "/v1/organization"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), ORGANIZATION);
  assert.equal(err.join(""), "");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, "GET");
  assert.equal(calls[0].url, "http://localhost:8080/v1/organization");
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("api get은 앞의 /가 없는 경로도 받는다", async () => {
  const { deps, calls } = await setup([json(200, ORGANIZATION)]);

  assert.equal(await run(["api", "get", "v1/organization"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/organization");
});

test("api get은 경로의 쿼리를 그대로 보낸다", async () => {
  const { deps, calls } = await setup([json(200, { months: [] })]);

  assert.equal(await run(["api", "get", "/v1/usage?month=2026-11"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/usage?month=2026-11");
});

test("api get의 --query는 인코딩해 덧붙이고 여러 번 쓸 수 있다", async () => {
  const replies = Array.from({ length: 3 }, () => json(200, {}));
  const { deps, calls } = await setup(replies);

  assert.equal(await run(["api", "get", "/v1/events", "--query", "month=2026-11", "--query", "q=한글 a&b=c"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/events?month=2026-11&q=%ED%95%9C%EA%B8%80+a%26b%3Dc");

  assert.equal(await run(["api", "get", "/v1/events?month=2026-11", "--query", "type=llm_request"], deps), EXIT_OK);
  assert.equal(calls[1].url, "http://localhost:8080/v1/events?month=2026-11&type=llm_request");

  assert.equal(await run(["api", "get", "/v1/events?", "--query", "properties={\"model\":\"x\"}"], deps), EXIT_OK);
  assert.equal(calls[2].url, "http://localhost:8080/v1/events?properties=%7B%22model%22%3A%22x%22%7D");
});

test("api get의 경로나 쿼리가 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  for (const args of [
    ["/invoices"],
    ["invoices"],
    ["/actuator/health"],
    ["https://evil.example/v1/invoices"],
    ["//evil.example/v1/invoices"],
    ["/v1/../actuator/health"],
    ["/v1/%2E%2E/actuator/health"],
    ["/v1/invoices/./draft"],
    ["/v1/invoices#x"],
    ["/v1/..\\actuator/health"],
    ["/v1/.\t./actuator/health"],
    ["/v1/..\\v1\\organization"],
    ["/v1/invoices\n"],
    ["/v1/invoices", "--query", "month"],
    ["/v1/invoices", "--query", "=2026-11"],
  ]) {
    const { deps, calls, out, stderrJson } = await setup([]);
    assert.equal(await run(["api", "get", ...args], deps), EXIT_USAGE, args.join(" "));
    assert.equal(calls.length, 0);
    assert.equal(out.join(""), "");
    assert.match((stderrJson() as { code: string }).code, /^invalid_(path|query)$/);
  }
});

test("api get은 /v1/이 빠진 경로에 고친 경로를 알려 준다", async () => {
  const { deps, stderrJson } = await setup([]);

  assert.equal(await run(["api", "get", "/invoices/draft"], deps), EXIT_USAGE);
  assert.deepEqual(stderrJson(), { code: "invalid_path", message: "경로는 /v1/로 시작합니다. 예: /v1/invoices/draft" });
});

test("api get으로 API 키가 부를 수 없는 경로를 부르면 서버의 403과 종료 코드 1이다", async () => {
  const denied = { code: "permission_denied", message: "이 요청을 할 권한이 없습니다" };
  const { deps, out, stderrJson } = await setup([json(403, denied)]);

  assert.equal(await run(["api", "get", "/v1/api-keys"], deps), EXIT_FAILED);
  assert.equal(out.join(""), "");
  assert.deepEqual(stderrJson(), denied);
});

test("api get으로 없는 경로를 부르면 서버의 404와 종료 코드 1이다", async () => {
  const missing = { code: "endpoint_not_found", message: "요청한 경로가 없습니다" };
  const { deps, stderrJson } = await setup([json(404, missing)]);

  assert.equal(await run(["api", "get", "/v1/nope"], deps), EXIT_FAILED);
  assert.deepEqual(stderrJson(), missing);
});

test("api get에서 서버가 429면 종료 코드 3이다", async () => {
  const { deps } = await setup([json(429, { code: "rate_limit_exceeded", message: "한도" })]);

  assert.equal(await run(["api", "get", "/v1/organization"], deps), EXIT_RETRYABLE);
});

test("api get은 CSV 내보내기(/export) 경로를 보내기 전에 거절하고 종료 코드 2다", async () => {
  for (const path of ["/v1/events/export?month=2026-11", "/v1/customers/export", "/v1/billable-metrics/export", "/v1/invoices/draft/%65xport"]) {
    const { deps, calls, out, stderrJson } = await setup([]);
    assert.equal(await run(["api", "get", path], deps), EXIT_USAGE, path);
    assert.equal(calls.length, 0);
    assert.equal(out.join(""), "");
    assert.equal((stderrJson() as { code: string }).code, "unsupported_path");
  }
});

test("api get은 export가 마지막 구간이 아니면 그대로 보낸다", async () => {
  const { deps, calls } = await setup([json(200, {})]);

  assert.equal(await run(["api", "get", "/v1/customers/export-team"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/customers/export-team");
});

test("api get의 성공 응답이 JSON이 아니면 종료 코드 1과 서버 주소를 확인하라는 안내를 낸다", async () => {
  const html = new Response("<!doctype html><html></html>", { status: 200, headers: { "Content-Type": "text/html" } });
  const { deps, out, stderrJson } = await setup([html]);

  assert.equal(await run(["api", "get", "/v1/organization"], deps), EXIT_FAILED);
  assert.equal(out.join(""), "");
  const error = stderrJson() as { code: string; message: string };
  assert.equal(error.code, "unexpected_error");
  assert.match(error.message, /JSON이 아닙니다.*METERENGINE_API_BASE_URL/);
});

test("api get에 하위 명령 없이 api만 쓰면 종료 코드 2다", async () => {
  const { deps, calls } = await setup([]);

  assert.equal(await run(["api"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
});
