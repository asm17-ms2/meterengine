import assert from "node:assert/strict";
import { test } from "node:test";

import { DEFAULT_BRIDGE_URL, parseTargets, type TargetEnv } from "./config.ts";

const SECRET_KEY = "me_test_AbCdEfGhIjKlMnOpQrStUvWxYz012345";

function envOf(targets: string | undefined, apiKey?: string): TargetEnv {
  const entries = [
    ["METERENGINE_TARGETS", targets],
    ["METERENGINE_API_KEY", apiKey],
  ].filter(([, value]) => value !== undefined);
  return Object.fromEntries(entries);
}

function assertErrorWithoutKey(targets: string): void {
  assert.throws(
    () => parseTargets(envOf(targets, SECRET_KEY)),
    (error: unknown) => error instanceof Error && !error.message.includes(SECRET_KEY),
  );
}

test("TARGETS가 없으면 local 대상 하나를 쓴다", () => {
  assert.deepEqual(parseTargets(envOf(undefined, SECRET_KEY)), [
    { name: "local", base_url: "http://localhost:8080", api_key: SECRET_KEY },
  ]);
  assert.deepEqual(parseTargets(envOf(undefined)), [{ name: "local", base_url: "http://localhost:8080", api_key: "" }]);
});

test("TARGETS JSON 배열을 해석한다", () => {
  const targets = [
    { name: "local", base_url: "http://localhost:8080", api_key: SECRET_KEY },
    { name: "test-slo", base_url: "https://slo.example.com", api_key: "me_live_x" },
  ];
  assert.deepEqual(parseTargets(envOf(JSON.stringify(targets), "ignored")), targets);
});

test("이름이 겹치면 Error이고 메시지에 키 값이 없다", () => {
  const targets = [
    { name: "local", base_url: "http://localhost:8080", api_key: SECRET_KEY },
    { name: "local", base_url: "http://localhost:8081", api_key: SECRET_KEY },
  ];
  assertErrorWithoutKey(JSON.stringify(targets));
});

test("잘못된 JSON은 Error이고 메시지에 키 값이 없다", () => {
  assertErrorWithoutKey(`[{"name": "local", "api_key": "${SECRET_KEY}"`);
});

test("빈 이름, http(s) 아닌 base_url, 빈 api_key, 배열 아닌 값은 Error다", () => {
  const valid = { name: "local", base_url: "http://localhost:8080", api_key: SECRET_KEY };
  assertErrorWithoutKey(JSON.stringify([{ ...valid, name: "" }]));
  assertErrorWithoutKey(JSON.stringify([{ ...valid, base_url: "ftp://localhost" }]));
  assertErrorWithoutKey(JSON.stringify([{ ...valid, base_url: "localhost:8080" }]));
  assertErrorWithoutKey(JSON.stringify([{ ...valid, api_key: "" }]));
  assertErrorWithoutKey(JSON.stringify(valid));
  assertErrorWithoutKey("[]");
});

test("브리지 기본 주소", () => {
  assert.equal(DEFAULT_BRIDGE_URL, "http://127.0.0.1:4318");
});

test("각 대상의 업무 환경을 보존하고 생략하면 키의 환경을 사용한다", () => {
  const testTarget = { name: "test", base_url: "http://localhost:8080", api_key: SECRET_KEY, environment: "test" };
  const liveTarget = { ...testTarget, name: "live", environment: "live" };
  assert.deepEqual(parseTargets(envOf(JSON.stringify([testTarget, liveTarget]))), [testTarget, liveTarget]);
  assert.equal(parseTargets(Object.fromEntries([["METERENGINE_API_KEY", SECRET_KEY], ["METERENGINE_ENVIRONMENT", "test"]]))[0].environment, "test");
  assertErrorWithoutKey(JSON.stringify([{ ...testTarget, environment: "legacy" }]));
  assert.throws(() => parseTargets(Object.fromEntries([["METERENGINE_ENVIRONMENT", "other"]])));
});

test("대상별 웹훅 비밀값을 서버 설정으로 보존하고 잘못된 타입은 거절한다", () => {
  const target = { name: "test", base_url: "http://localhost:8080", api_key: SECRET_KEY, environment: "test", webhook_secret: "whsec_private-test-value" };
  assert.deepEqual(parseTargets(envOf(JSON.stringify([target]))), [target]);
  assertErrorWithoutKey(JSON.stringify([{ ...target, webhook_secret: { secret: target.webhook_secret } }]));
});
