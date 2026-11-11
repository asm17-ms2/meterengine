import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import test from "node:test";

const here = new URL(".", import.meta.url);
const fixtures = ["contract-server.mjs", "payment-registration-server.mjs", "workspace-contract-server.mjs"];
const specs = readdirSync(here).filter(name => name.endsWith(".contract.spec.ts"));

// 고정 포트는 CI 러너의 임시 포트 범위와 겹쳐 앞 단계가 남긴 연결과 부딪힌다
test("contract fixtures and specs never pin a port", () => {
  for (const name of [...fixtures, ...specs]) {
    const source = readFileSync(new URL(name, here), "utf8");
    assert.doesNotMatch(source, /(?:localhost|127\.0\.0\.1):\d/, `${name} names a fixed address`);
    assert.doesNotMatch(source, /listen\(\s*[1-9]/, `${name} listens on a fixed port`);
  }
});
