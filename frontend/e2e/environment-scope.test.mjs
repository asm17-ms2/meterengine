import assert from "node:assert/strict";
import test from "node:test";

test("only exact shared route templates omit the business environment", async () => {
  const { requiresWorkspaceEnvironment } = await import("../src/lib/api/environment-scope.ts");
  for (const path of ["/v1/customers", "/v1/customers/search", "/v1/customers/options", "/v1/events/export", "/v1/audit-logs", "/v1/api-keys", "/v1/api-keys/key", "/v1/api-keys/key/scopes", "/v1/organization/memberships", "/v1/organization/members/id/extra", "/v1/organization/members/", "/v1/sessions/current/extra"]) assert.equal(requiresWorkspaceEnvironment(path), true, path);
  for (const path of ["/v1/sessions/current", "/v1/organization", "/v1/organization/members", "/v1/organization/members/id", "/v1/organization/members/id/role", "/v1/organization/members/id/scopes", "/v1/organization/invitations", "/v1/organization/invitations/id", "/v1/public/invoice-links/lookup", "/v1/organization/members?environment=live"]) assert.equal(requiresWorkspaceEnvironment(path), false, path);
});
