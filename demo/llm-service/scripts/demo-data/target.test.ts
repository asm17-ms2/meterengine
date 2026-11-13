import assert from "node:assert/strict";
import { test } from "node:test";
import { checkTarget } from "./target.ts";

const REMOTE = { baseUrlOption: "https://seed-guard.invalid", confirmHost: "seed-guard.invalid" };

test("기본은 로컬이다", () => {
  const target = checkTarget({});
  assert.equal(target.url.hostname, "127.0.0.1");
  assert.equal(target.isLocal, true);
  assert.equal(checkTarget({ baseUrlEnv: "http://localhost:38247" }).url.port, "38247");
  assert.equal(checkTarget({ baseUrlOption: "http://[::1]:8080" }).url.hostname, "[::1]");
  assert.equal(checkTarget({ environmentEnv: "test" }).isLocal, true);
  assert.equal(checkTarget({ environmentEnv: "" }).isLocal, true);
});

test("실제 환경(live)은 로컬 주소에서도, 확인 인자를 다 줘도 받지 않는다", () => {
  for (const environmentEnv of ["live", "LIVE", "production", " test"]) {
    assert.throws(() => checkTarget({ environmentEnv }), /테스트 환경에만/, environmentEnv);
    assert.throws(() => checkTarget({ baseUrlOption: "http://127.0.0.1:8080", environmentEnv }), /테스트 환경에만/, environmentEnv);
    assert.throws(() => checkTarget({ baseUrlEnv: "http://localhost:8080", confirmHost: "localhost", environmentEnv }), /테스트 환경에만/, environmentEnv);
    assert.throws(() => checkTarget({ ...REMOTE, environmentEnv }), /테스트 환경에만/, environmentEnv);
  }
});

test("다르게 적은 로컬 주소도 로컬로 보되 확인 인자가 있으면 호스트 이름을 맞춰 본다", () => {
  assert.equal(checkTarget({ baseUrlOption: "http://127.1:8080" }).url.hostname, "127.0.0.1");
  assert.equal(checkTarget({ baseUrlOption: "http://0x7f000001:8080" }).isLocal, true);
  assert.equal(checkTarget({ baseUrlOption: "http://127.0.0.1:8080", confirmHost: "127.0.0.1" }).isLocal, true);
  assert.throws(() => checkTarget({ baseUrlOption: "http://127.0.0.1:8080", confirmHost: "localhost" }), /다릅니다/);
});

test("로컬이 아닌 주소는 환경 변수만으로 돌지 않는다", () => {
  assert.throws(() => checkTarget({ baseUrlEnv: "https://seed-guard.invalid" }), /환경 변수만으로/);
  assert.throws(() => checkTarget({ baseUrlEnv: "https://seed-guard.invalid", confirmHost: "seed-guard.invalid" }), /환경 변수만으로/);
});

test("로컬이 아닌 주소는 같은 호스트의 --confirm-host와 https가 모두 있어야 한다", () => {
  assert.throws(() => checkTarget({ baseUrlOption: "https://seed-guard.invalid" }), /--confirm-host/);
  assert.throws(() => checkTarget({ baseUrlOption: "https://seed-guard.invalid", confirmHost: "other.invalid" }), /다릅니다/);
  assert.throws(() => checkTarget({ baseUrlOption: "https://seed-guard.invalid", confirmHost: "seed-guard" }), /다릅니다/);
  assert.throws(() => checkTarget({ baseUrlOption: "http://seed-guard.invalid", confirmHost: "seed-guard.invalid" }), /https/);
  const target = checkTarget(REMOTE);
  assert.equal(target.isLocal, false);
  assert.equal(target.url.origin, "https://seed-guard.invalid");
});

test("경로, 계정, 쿼리, 조각이 붙은 주소는 받지 않는다", () => {
  for (const baseUrlOption of [
    "http://127.0.0.1:38247/v1", "http://user:pw@127.0.0.1:38247", "ftp://127.0.0.1",
    "http://127.0.0.1:38247/?a=1", "http://127.0.0.1:38247/?", "http://127.0.0.1:38247?", "http://127.0.0.1:38247/#x", "http://127.0.0.1:38247#",
    "https://seed-guard.invalid/?", "https://seed-guard.invalid/#",
  ]) {
    assert.throws(() => checkTarget({ baseUrlOption, confirmHost: new URL(baseUrlOption).hostname }), /형식/, baseUrlOption);
  }
});
