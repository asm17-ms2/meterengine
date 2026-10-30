import assert from "node:assert/strict";
import test from "node:test";
import { canPerformWorkspaceOperation, hasScope, isAccessScope, scopeSummary, tenantRequestScope } from "../src/lib/auth/permissions.ts";

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

test("customer creation requires ingestion while customer updates and archival require admin", () => {
  assert.equal(canPerformWorkspaceOperation(["read", "ingest"], "member", "createCustomer"), true);
  assert.equal(canPerformWorkspaceOperation(["read"], "member", "createCustomer"), false);
  for (const operation of ["updateCustomer", "deleteCustomer", "restoreCustomer"]) {
    assert.equal(canPerformWorkspaceOperation(["ingest"], "member", operation), false);
    assert.equal(canPerformWorkspaceOperation(["admin"], "admin", operation), true);
  }
});

test("read-only cannot enter custom payment, pricing, key or restoration mutations", () => {
  for (const operation of ["createPaymentCancellation", "createPriceRate", "finalizeInvoices", "restoreBillableMetric", "createApiKey", "updateApiKeyScopes", "deleteApiKey", "createPaymentMethodRegistration"]) {
    assert.equal(canPerformWorkspaceOperation(["read"], "member", operation), false);
    assert.equal(canPerformWorkspaceOperation(["admin"], "admin", operation), true);
  }
});

test("transport classifies ingestion paths exactly and keeps every other mutation admin", () => {
  for (const path of ["/v1/customers", "/v1/events", "/v1/event-batches"]) {
    assert.equal(tenantRequestScope(path), "read");
    assert.equal(tenantRequestScope(`${path}?test=true`, "POST"), "ingest");
    assert.equal(tenantRequestScope(path, "DELETE"), "admin");
  }
  for (const path of ["/v1/customers/customer-id", "/v1/events/event-id/reversals", "/v1/event-batches/fake", "/v1/api-keys/key/scopes", "/v1/invoices?month=2026-10"]) assert.equal(tenantRequestScope(path, "POST"), "admin");
});

test("permission summaries communicate effective access without inferring roles", () => {
  assert.equal(scopeSummary(["admin"]), "전체 관리");
  assert.equal(scopeSummary(["read", "ingest"]), "조회, 수집");
  assert.equal(scopeSummary(["ingest"]), "수집");
  assert.equal(scopeSummary(undefined), "권한 없음");
});

test("organization roles have one label definition that matches the member list", async () => {
  const permissions = await import("../src/lib/auth/permissions.ts");
  assert.deepEqual(permissions.ORGANIZATION_ROLE_LABELS, { owner: "대표관리자", admin: "관리자", member: "일반 사용자" });
});


test("owner-only workspace operations need the owner role with admin scope", () => {
  for (const operation of ["createMember", "updateMemberRole", "updateMemberScopes", "archiveMember", "createOrganizationInvitation", "revokeOrganizationInvitation"]) {
    assert.equal(canPerformWorkspaceOperation(["admin"], "owner", operation), true);
    for (const role of ["admin", "member", null]) assert.equal(canPerformWorkspaceOperation(["admin"], role, operation), false);
    assert.equal(canPerformWorkspaceOperation(["read", "ingest"], "owner", operation), false);
  }
});
