import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { TEST_KEY, json, setup } from "../testing.ts";

const CUSTOMER_ID = "a728e7b6-d82b-4f3c-a960-a66a02794c1d";
const CUSTOMER = {
  id: CUSTOMER_ID,
  name: "아크메 주식회사",
  billing_email: null,
  created_at: "2026-11-14T01:29:57.45029Z",
  external_id: "acme-001",
  archived_at: null,
  version: 0,
};
const NOT_FOUND = { code: "customer_not_found", message: "고객을 찾을 수 없습니다" };

test("customers list는 서버 응답 JSON을 그대로 낸다", async () => {
  const body = { customers: [CUSTOMER] };
  const { deps, calls, stdoutJson, err } = await setup([json(200, body)]);

  assert.equal(await run(["customers", "list"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), body);
  assert.equal(err.join(""), "");
  assert.equal(calls[0].url, "http://localhost:8080/v1/customers");
  assert.equal(calls[0].method, "GET");
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("customers list는 외부 ID와 보관 상태를 쿼리로 보낸다", async () => {
  const { deps, calls } = await setup([json(200, { customers: [] })]);

  assert.equal(await run(["customers", "list", "--external-id", "acme 001", "--status", "all"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/customers?external_id=acme+001&status=all");
});

test("customers list는 외부 ID 앞뒤 공백을 지우지 않고 보낸다. 서버가 공백까지 외부 ID로 저장한다", async () => {
  const { deps, calls } = await setup([json(200, { customers: [] })]);

  assert.equal(await run(["customers", "list", "--external-id", " acme-001 "], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/customers?external_id=+acme-001+");
});

test("customers list에 모르는 보관 상태를 주면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["customers", "list", "--status", "deleted"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_status");
});

test("customers list에 빈 외부 ID를 주면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls } = await setup([]);

  assert.equal(await run(["customers", "list", "--external-id", ""], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
});

test("customers get은 UUID로 조회해 서버 응답 JSON을 그대로 낸다", async () => {
  const { deps, calls, stdoutJson } = await setup([json(200, CUSTOMER)]);

  assert.equal(await run(["customers", "get", CUSTOMER_ID], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), CUSTOMER);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, `http://localhost:8080/v1/customers/${CUSTOMER_ID}`);
});

test("customers get에 외부 ID를 넣으면 고객 조회를 보내지 않고 외부 ID로 찾아 고객 UUID를 알려 준다", async () => {
  const { deps, calls, out, stderrJson } = await setup([json(200, { customers: [CUSTOMER] })]);

  assert.equal(await run(["customers", "get", "acme-001"], deps), EXIT_USAGE);
  assert.equal(out.join(""), "");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "http://localhost:8080/v1/customers?external_id=acme-001&status=all");
  const error = stderrJson() as { code: string; message: string; errors?: unknown };
  assert.equal(error.code, "external_id_instead_of_id");
  assert.match(error.message, new RegExp(CUSTOMER_ID));
  assert.equal(error.errors, undefined);
});

test("'/'가 든 외부 ID도 경로에 넣지 않고 외부 ID로 찾아 알려 준다", async () => {
  const externalId = "acme/e2e 1";
  const { deps, calls, stderrJson } = await setup([json(200, { customers: [{ ...CUSTOMER, external_id: externalId }] })]);

  assert.equal(await run(["customers", "get", externalId], deps), EXIT_USAGE);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "http://localhost:8080/v1/customers?external_id=acme%2Fe2e+1&status=all");
  assert.equal((stderrJson() as { code: string }).code, "external_id_instead_of_id");
});

test("UUID 모양의 외부 ID를 넣어 404가 나도 외부 ID로 찾아 알려 준다", async () => {
  const externalId = "5f0c2a8e-0000-4000-8000-000000000001";
  const { deps, calls, stderrJson } = await setup([json(404, NOT_FOUND), json(200, { customers: [{ ...CUSTOMER, external_id: externalId }] })]);

  assert.equal(await run(["customers", "get", externalId], deps), EXIT_USAGE);
  assert.equal(calls[0].url, `http://localhost:8080/v1/customers/${externalId}`);
  assert.equal(calls[1].url, `http://localhost:8080/v1/customers?external_id=${externalId}&status=all`);
  const error = stderrJson() as { code: string; message: string };
  assert.equal(error.code, "external_id_instead_of_id");
  assert.match(error.message, new RegExp(CUSTOMER_ID));
});

test("외부 ID로 찾은 고객이 보관한 고객이면 메시지에 함께 알린다", async () => {
  const { deps, stderrJson } = await setup([json(200, { customers: [{ ...CUSTOMER, archived_at: "2026-11-01T00:00:00Z" }] })]);

  assert.equal(await run(["customers", "get", "acme-001"], deps), EXIT_USAGE);
  assert.match((stderrJson() as { message: string }).message, /보관한 고객/);
});

test("UUID가 아닌 값이 외부 ID로도 없으면 invalid_customer_id로 종료 코드 2다", async () => {
  for (const lookup of [json(200, { customers: [] }), json(400, { code: "validation_error", message: "요청 값이 올바르지 않습니다" })]) {
    const { deps, calls, stderrJson } = await setup([lookup]);

    assert.equal(await run(["customers", "get", "acme-404"], deps), EXIT_USAGE);
    assert.equal(calls.length, 1);
    assert.equal((stderrJson() as { code: string }).code, "invalid_customer_id");
  }
});

test("외부 ID 길이 상한을 넘는 값은 아무것도 보내지 않고 invalid_customer_id다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["customers", "get", "x".repeat(256)], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_customer_id");
});

test("UUID가 아닌 값의 외부 ID 찾기가 거절되거나 실패하면 그 오류와 종료 코드를 낸다", async () => {
  const cases = [
    [json(401, { code: "unauthorized", message: "API 키가 올바르지 않습니다" }), EXIT_FAILED, "unauthorized"],
    [json(503, { code: "service_unavailable", message: "잠시 뒤" }), EXIT_RETRYABLE, "service_unavailable"],
    [new TypeError("fetch failed"), EXIT_RETRYABLE, "connection_failed"],
  ] as const;
  for (const [lookup, exitCode, code] of cases) {
    const { deps, stderrJson } = await setup([lookup]);

    assert.equal(await run(["customers", "get", "acme-001"], deps), exitCode);
    assert.equal((stderrJson() as { code: string }).code, code);
  }
});

test("UUID로 404가 나고 외부 ID로도 없거나 찾기가 실패하면 서버의 원래 오류를 그대로 낸다", async () => {
  for (const lookup of [json(200, { customers: [] }), json(503, { code: "service_unavailable", message: "잠시 뒤" }), new TypeError("fetch failed")]) {
    const { deps, calls, stderrJson } = await setup([json(404, NOT_FOUND), lookup]);

    assert.equal(await run(["customers", "get", CUSTOMER_ID], deps), EXIT_FAILED);
    assert.equal(calls.length, 2);
    assert.deepEqual(stderrJson(), NOT_FOUND);
  }
});

test("고객 조회가 다시 하면 될 수 있는 실패면 외부 ID로 찾지 않고 종료 코드 3이다", async () => {
  const { deps, calls } = await setup([json(503, { code: "service_unavailable", message: "잠시 뒤" })]);

  assert.equal(await run(["customers", "get", CUSTOMER_ID], deps), EXIT_RETRYABLE);
  assert.equal(calls.length, 1);
});

test("customers get에 빈 id를 주면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls } = await setup([]);

  assert.equal(await run(["customers", "get", ""], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
});
