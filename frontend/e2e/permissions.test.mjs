import assert from "node:assert/strict";
import test from "node:test";
import { canPerformWorkspaceOperation, hasScope, isAccessScope } from "../src/lib/auth/permissions.ts";

test("missing permissions and unrecognized operations fail closed", () => {
  for (const scopes of [null, undefined, [], ["operator"], ["owner"]]) {
    for (const scope of ["read", "ingest", "admin"]) assert.equal(hasScope(scopes, scope), false);
    assert.equal(canPerformWorkspaceOperation(scopes, "owner", "createMember"), false);
  }
  assert.equal(canPerformWorkspaceOperation(["admin"], "owner", "unknownOperation"), false);
  assert.equal(isAccessScope("operator"), false);
  assert.equal(hasScope(["admin"], "operator"), false);
  assert.equal(hasScope(["admin"], "owner"), false);
  assert.equal(hasScope(["admin"], ""), false);
});

test("admin includes reads and ingestion but reads and ingestion remain separate", () => {
  for (const scope of ["read", "ingest", "admin"]) assert.equal(hasScope(["admin"], scope), true);
  assert.equal(hasScope(["read"], "ingest"), false);
  assert.equal(hasScope(["ingest"], "read"), false);
  assert.equal(hasScope(["read", "ingest"], "admin"), false);
});

test("membership roles and platform operator never substitute for assigned scopes", () => {
  for (const role of ["owner", "admin", "member", "operator"]) {
    assert.equal(canPerformWorkspaceOperation(["read"], role, "createPaymentAttempt"), false);
    assert.equal(canPerformWorkspaceOperation(["read"], role, "updateApiKeyScopes"), false);
  }
  assert.equal(canPerformWorkspaceOperation(["admin"], "admin", "updateMemberScopes"), false);
  assert.equal(canPerformWorkspaceOperation(["admin"], "operator", "createMember"), false);
  assert.equal(canPerformWorkspaceOperation(["admin"], "owner", "updateMemberScopes"), true);
});

