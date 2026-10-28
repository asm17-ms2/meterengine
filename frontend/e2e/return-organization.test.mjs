import assert from "node:assert/strict";
import test from "node:test";
import { returnOrganizationId, withoutOrganizationId } from "../src/lib/auth/navigation.ts";

test("the organization in a return path is read and can be dropped without losing other filters", () => {
  assert.equal(returnOrganizationId("/billing?organization_id=B&month=2026-10"), "B");
  assert.equal(returnOrganizationId("/billing?month=2026-10"), null);
  assert.equal(withoutOrganizationId("/billing?organization_id=B&month=2026-10"), "/billing?month=2026-10");
  assert.equal(withoutOrganizationId("/customers?organization_id=B"), "/customers");
  assert.equal(withoutOrganizationId("/customers?q=abc"), "/customers?q=abc");
});
