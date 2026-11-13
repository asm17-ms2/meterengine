import assert from "node:assert/strict";
import test from "node:test";
import { environmentNoticeClass, environmentOptions, environmentQueryValue, isAllowedEnvironment, isClosedLegacyEnvironment, isLockedEnvironment, readEnvironment, showsLegacyEnvironment, withoutClosedLegacyEnvironment } from "../src/lib/environment.ts";

test("only the live environment notice uses the warning style", () => {
  assert.match(environmentNoticeClass("live"), /\benvironment-notice-live\b/);
  for (const environment of ["legacy", "test"]) {
    assert.equal(environmentNoticeClass(environment), "environment-notice");
  }
});

test("a missing or unknown environment is the test environment", () => {
  for (const value of [null, undefined, "", "unknown", "LIVE"]) assert.equal(readEnvironment(value), "test", String(value));
  assert.equal(readEnvironment("live"), "live");
  assert.equal(readEnvironment("legacy"), "legacy");
});

test("the environment list leaves out legacy data unless the server flag is true", () => {
  for (const flag of [undefined, null, "", "false", "1", "TRUE"]) {
    const showLegacy = showsLegacyEnvironment(flag);
    assert.equal(showLegacy, false, String(flag));
    assert.deepEqual(environmentOptions(showLegacy), ["test", "live"]);
    assert.equal(isAllowedEnvironment("legacy", showLegacy), false);
  }
  assert.equal(showsLegacyEnvironment("true"), true);
  assert.deepEqual(environmentOptions(true), ["test", "live", "legacy"]);
  assert.equal(isAllowedEnvironment("legacy", true), true);
  for (const value of ["test", "live"]) assert.equal(isAllowedEnvironment(value, false), true);
  assert.equal(isAllowedEnvironment("unknown", true), false);
});

test("a server without legacy data drops only the legacy value from a saved address and keeps the rest", () => {
  assert.equal(isClosedLegacyEnvironment("legacy", false), true);
  assert.equal(isClosedLegacyEnvironment("legacy", true), false);
  for (const value of [null, undefined, "", "test", "live", "unknown"]) assert.equal(isClosedLegacyEnvironment(value, false), false, String(value));
  assert.equal(withoutClosedLegacyEnvironment("/customers?organization_id=org-1&environment=legacy&month=2026-09&customer_id=c-1", false), "/customers?organization_id=org-1&month=2026-09&customer_id=c-1");
  assert.equal(withoutClosedLegacyEnvironment("/overview?environment=legacy", false), "/overview");
  for (const path of ["/customers?environment=legacy&month=2026-09", "/customers?environment=live", "/customers?environment=unknown", "/customers"]) {
    assert.equal(withoutClosedLegacyEnvironment(path, path.includes("legacy")), path, path);
  }
});

test("only a non-default environment is written to the address", () => {
  assert.equal(environmentQueryValue("test"), undefined);
  assert.equal(environmentQueryValue("live"), "live");
  assert.equal(environmentQueryValue("legacy"), "legacy");
});

test("until the live environment opens, only the test environment is usable", () => {
  assert.equal(isLockedEnvironment("test", false), false);
  for (const environment of ["live", "legacy"]) assert.equal(isLockedEnvironment(environment, false), true, environment);
  for (const environment of ["test", "live", "legacy"]) assert.equal(isLockedEnvironment(environment, true), false, environment);
});
