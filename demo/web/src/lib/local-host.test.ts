import assert from "node:assert/strict";
import { test } from "node:test";

import { isLocalHost } from "./local-host.ts";

test("로컬 Host는 통과한다", () => {
  for (const host of ["127.0.0.1:3100", "localhost:3100", "LOCALHOST", "[::1]:3100", "127.0.0.1"]) {
    assert.equal(isLocalHost(host), true, host);
  }
});

test("로컬이 아닌 Host와 빈 값은 막는다", () => {
  for (const host of ["evil.example:3100", "localhost.evil.example:3100", "127.0.0.1.nip.io:3100", "", null]) {
    assert.equal(isLocalHost(host), false, String(host));
  }
});
