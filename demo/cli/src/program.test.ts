import assert from "node:assert/strict";
import { stat } from "node:fs/promises";
import { test } from "node:test";
import { API_KEY_ENV, credentialsPath } from "./credentials.ts";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "./exit-code.ts";
import { BASE_URL_ENV, run } from "./program.ts";
import { TEST_KEY, json, setup } from "./testing.ts";

const DRAFT = { month: "2026-11", total_amount: 2000, customers: [] };

test("invoices draft는 서버 응답 JSON을 그대로 표준 출력에 낸다", async () => {
  const { deps, out, err, calls } = await setup([json(200, DRAFT)]);

  const code = await run(["invoices", "draft", "--month", "2026-11"], deps);

  assert.equal(code, EXIT_OK);
  assert.deepEqual(JSON.parse(out.join("")), DRAFT);
  assert.equal(err.join(""), "");
  assert.equal(calls[0].url, "http://localhost:8080/v1/invoices/draft?month=2026-11");
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("서버가 거절하면 종료 코드 1과 서버의 오류 JSON을 표준 오류에 낸다", async () => {
  const { deps, out, err } = await setup([json(401, { code: "invalid_credentials", message: "인증 정보가 올바르지 않습니다" })]);

  const code = await run(["invoices", "draft"], deps);

  assert.equal(code, EXIT_FAILED);
  assert.equal(out.join(""), "");
  assert.deepEqual(JSON.parse(err.join("")), { code: "invalid_credentials", message: "인증 정보가 올바르지 않습니다" });
});

test("다시 보내면 될 수 있는 실패는 종료 코드 3이다", async () => {
  for (const reply of [json(503, { code: "service_unavailable", message: "잠시 뒤" }), json(429, { code: "rate_limit_exceeded", message: "한도" }), new TypeError("fetch failed")]) {
    const { deps } = await setup([reply]);
    assert.equal(await run(["invoices", "draft"], deps), EXIT_RETRYABLE);
  }
});

test("키가 없으면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, err, calls } = await setup([], { env: {} });

  assert.equal(await run(["invoices", "draft"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal(JSON.parse(err.join("")).code, "missing_api_key");
});

test("모르는 옵션은 종료 코드 2이고 --help는 0이다", async () => {
  const unknown = await setup([]);
  assert.equal(await run(["invoices", "draft", "--monthh", "2026-11"], unknown.deps), EXIT_USAGE);
  assert.equal(JSON.parse(unknown.err.join("")).code, "invalid_usage");

  const help = await setup([]);
  assert.equal(await run(["invoices", "draft", "--help"], help.deps), EXIT_OK);
  assert.match(help.out.join(""), /--month/);
});

test("login은 받은 키를 저장하고 이후 명령이 그 키를 쓴다", async () => {
  const { deps, out, calls } = await setup([json(200, DRAFT)], { env: {} });

  assert.equal(await run(["login"], deps), EXIT_OK);
  assert.equal((await stat(credentialsPath(deps.configDir))).mode & 0o777, 0o600);
  assert.equal(JSON.parse(out.join("")).mode, "test");

  assert.equal(await run(["invoices", "draft"], deps), EXIT_OK);
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("login에 형식이 틀린 키를 넣으면 저장하지 않고 종료 코드 2다", async () => {
  const { deps } = await setup([], { env: {}, secret: "me_test_xxxxxxxx" });

  assert.equal(await run(["login"], deps), EXIT_USAGE);
  await assert.rejects(stat(credentialsPath(deps.configDir)));
});

test("하위 명령 없이 묶음 명령만 쓰면 표준 오류에 도움말 없이 하위 명령 목록을 담은 JSON만 낸다", async () => {
  for (const [argv, names] of [[[], /login, usage, events/], [["events"], /get, list, types/], [["verify"], /sent/]] as const) {
    const { deps, out, err, calls } = await setup([]);

    assert.equal(await run([...argv], deps), EXIT_USAGE);
    assert.equal(out.join(""), "");
    assert.equal(calls.length, 0);
    const error = JSON.parse(err.join("")) as { code: string; message: string };
    assert.equal(error.code, "invalid_usage");
    assert.match(error.message, names);
  }
});

test("하위 명령 도움말에도 전역 옵션 --profile이 나온다", async () => {
  const { deps, out } = await setup([]);

  assert.equal(await run(["usage", "--help"], deps), EXIT_OK);
  assert.match(out.join(""), /Global Options:[\s\S]*--profile <name>/);
});

test("서버 주소가 http(s) 주소가 아니면 보내지 않고 종료 코드 2다", async () => {
  for (const baseUrl of ["localhost:8080", "htp://localhost:8080", "http://u:p@localhost:8080", "http://localhost:8080?x=1", "http://"]) {
    const { deps, calls, err } = await setup([], { env: { [API_KEY_ENV]: TEST_KEY, [BASE_URL_ENV]: baseUrl } });

    assert.equal(await run(["invoices", "draft"], deps), EXIT_USAGE);
    assert.equal(calls.length, 0);
    assert.equal(JSON.parse(err.join("")).code, "invalid_base_url");
  }
});
