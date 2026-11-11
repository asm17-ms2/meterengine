import assert from "node:assert/strict";
import { mkdtemp, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { API_KEY_ENV, credentialsPath } from "./credentials.ts";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "./exit-code.ts";
import { BASE_URL_ENV, run } from "./program.ts";
import type { CliDeps } from "./program.ts";

const TEST_KEY = "me_test_wiCp766QYNqhyYm4Z7Xq9wYosNvQCeVC";
const DRAFT = { month: "2026-11", total_amount: 2000, customers: [] };

type Call = { url: string; headers: Headers };

async function setup(replies: (Response | Error)[], env: NodeJS.ProcessEnv = { [API_KEY_ENV]: TEST_KEY }, secret = TEST_KEY) {
  const out: string[] = [];
  const err: string[] = [];
  const calls: Call[] = [];
  const deps: CliDeps = {
    env: { [BASE_URL_ENV]: "http://localhost:8080", ...env },
    configDir: join(await mkdtemp(join(tmpdir(), "meterengine-cli-")), "meterengine"),
    stdout: (text) => out.push(text),
    stderr: (text) => err.push(text),
    readSecret: async () => secret,
    clientOptions: {
      maxRetries: 0,
      fetch: async (input, init = {}) => {
        calls.push({ url: String(input), headers: new Headers(init.headers) });
        const reply = replies.shift();
        if (reply === undefined) throw new Error("준비한 응답보다 호출이 많다");
        if (reply instanceof Error) throw reply;
        return reply;
      },
    },
  };
  return { deps, out, err, calls };
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

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
  const { deps, err, calls } = await setup([], {});

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
  const { deps, out, calls } = await setup([json(200, DRAFT)], {});

  assert.equal(await run(["login"], deps), EXIT_OK);
  assert.equal((await stat(credentialsPath(deps.configDir))).mode & 0o777, 0o600);
  assert.equal(JSON.parse(out.join("")).mode, "test");

  assert.equal(await run(["invoices", "draft"], deps), EXIT_OK);
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("login에 형식이 틀린 키를 넣으면 저장하지 않고 종료 코드 2다", async () => {
  const { deps } = await setup([], {}, "me_test_xxxxxxxx");

  assert.equal(await run(["login"], deps), EXIT_USAGE);
  await assert.rejects(stat(credentialsPath(deps.configDir)));
});
