import assert from "node:assert/strict";
import test from "node:test";
import * as environmentContract from "../src/lib/environment.ts";
const { environmentNoticeClass } = environmentContract;

test("business environments accept exact test/live strings and default old UI links to test", () => {
  assert.deepEqual(Object.keys(environmentContract.ENVIRONMENT_LABELS).sort(), ["live", "test"]);
  assert.deepEqual(Object.keys(environmentContract.ENVIRONMENT_SWITCH_TITLES).sort(), ["live", "test"]);
  for (const value of [undefined, null, "", "legacy", "TEST", " test "]) assert.equal(environmentContract.isWorkspaceEnvironment(value), false);
  for (const value of ["test", "live"]) assert.equal(environmentContract.isWorkspaceEnvironment(value), true);
  for (const value of [undefined, null, "", "legacy", "test"]) assert.equal(environmentContract.readEnvironment(value), "test");
  assert.equal(environmentContract.readEnvironment("live"), "live");
});

test("only the live environment notice uses the warning style", () => {
  assert.match(environmentNoticeClass("live"), /\benvironment-notice-live\b/);
  for (const environment of ["test"]) {
    assert.equal(environmentNoticeClass(environment), "environment-notice");
  }
});
