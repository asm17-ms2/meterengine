import assert from "node:assert/strict";
import test from "node:test";
import { ACCESS_SCOPE_LABELS, scopeLabels, scopeSummary } from "../src/lib/auth/permissions.ts";

test("scope labels use one name per scope in read, ingest, admin order", () => {
  assert.equal(ACCESS_SCOPE_LABELS.ingest, "수집");
  assert.deepEqual(scopeLabels(["ingest", "read"]), ["조회", "수집"]);
  assert.deepEqual(scopeLabels(["admin", "x"]), ["전체 관리", "x"]);
  assert.equal(scopeSummary(["read", "ingest"]), "조회, 수집");
  assert.equal(scopeSummary(["admin"]), "전체 관리");
});
