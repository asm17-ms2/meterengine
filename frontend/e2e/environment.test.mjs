import assert from "node:assert/strict";
import test from "node:test";
import { environmentNoticeClass } from "../src/lib/environment.ts";

test("only the live environment notice uses the warning style", () => {
  assert.match(environmentNoticeClass("live"), /\benvironment-notice-live\b/);
  for (const environment of ["legacy", "test"]) {
    assert.equal(environmentNoticeClass(environment), "environment-notice");
  }
});
