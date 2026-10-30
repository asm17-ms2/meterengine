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

test("dropping an organization the session could not open also drops what belongs to it", () => {
  const id = "0f0e0d0c-0b0a-4908-8706-050403020100";
  assert.equal(withoutOrganizationId("/events?organization_id=B&customer_id=C&month=2026-10"), "/events?month=2026-10");
  assert.equal(withoutOrganizationId(`/payments/${id}?organization_id=B`), "/payments");
  assert.equal(withoutOrganizationId(`/customers/${id}?organization_id=B&month=2026-10`), "/customers?month=2026-10");
  assert.equal(withoutOrganizationId(`/integrations/${id}?organization_id=B`), "/integrations");
});
