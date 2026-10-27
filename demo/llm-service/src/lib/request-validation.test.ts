import assert from "node:assert/strict";
import { test } from "node:test";
import { assertRequest, readJson } from "./request-validation.ts";
import { ServiceError } from "./service-error.ts";

const CONFIG = { appOrigin: "http://localhost:3200" };

test("설정한 Host와 정확한 Origin에서만 상태를 변경한다", () => {
  const valid = new Request("http://localhost:3200/api/generations", { method: "POST", headers: { Host: "localhost:3200", Origin: CONFIG.appOrigin } });
  assert.doesNotThrow(() => assertRequest(valid, CONFIG, true));
  for (const headers of [
    { Host: "evil.example", Origin: CONFIG.appOrigin },
    { Host: "localhost:3200", Origin: "https://evil.example" },
    { Host: "localhost:3200", Origin: "null" },
    { Host: "localhost:3200" },
  ] as Record<string, string>[]) {
    assert.throws(() => assertRequest(new Request("http://localhost:3200/api/generations", { headers }), CONFIG, true), error => error instanceof ServiceError && error.status === 403);
  }
});

test("읽기에도 Host를 확인하고 Origin 없는 같은 서버 조회는 허용한다", () => {
  assert.doesNotThrow(() => assertRequest(new Request("http://localhost:3200/api/session", { headers: { Host: "localhost:3200" } }), CONFIG));
  assert.throws(() => assertRequest(new Request("http://localhost:3200/api/session", { headers: { Host: "localhost:3200.evil.example" } }), CONFIG));
});

test("JSON이 아닌 폼과 깨진 JSON, 너무 큰 본문을 거절한다", async () => {
  await assert.rejects(() => readJson(new Request("http://localhost", { method: "POST", headers: { "Content-Type": "text/plain" }, body: "{}" })), error => error instanceof ServiceError && error.status === 400);
  await assert.rejects(() => readJson(new Request("http://localhost", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{" })), error => error instanceof ServiceError && error.status === 400);
  await assert.rejects(() => readJson(new Request("http://localhost", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ prompt: "x".repeat(40000) }) })), error => error instanceof ServiceError && error.status === 413);
  assert.deepEqual(await readJson(new Request("http://localhost", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: '{"prompt":"hello"}' })), { prompt: "hello" });
});
