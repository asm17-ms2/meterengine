import assert from "node:assert/strict";
import test from "node:test";
import { workspaceHref } from "../src/lib/workspace-href.ts";
import { safeReturnPath } from "../src/lib/auth/navigation.ts";
import { environmentReadyPath, environmentSwitchPath } from "../src/lib/environment.ts";

const source = new URLSearchParams({ organization_id: "rendered-organization", environment: "test", month: "2026-10", customer_id: "rendered-customer" });

test("all business routes support each workspace environment", () => {
  for (const path of ["/overview", "/customers", "/billable-metrics", "/billable-metric-prices", "/pricing", "/usage", "/events", "/event-issues", "/event-rejections", "/billing", "/invoices", "/payments", "/integrations", "/settings", "/members", "/audit"]) assert.equal(environmentReadyPath(path), true, path);
  for (const path of ["/admin", "/organizations", "/payment-method-registrations/token", "/customers-other"]) assert.equal(environmentReadyPath(path), false, path);
});

test("switching environment clears every resource identifier and retains its list route", () => {
  for (const resource of ["customers", "billable-metrics", "payments", "invoices", "integrations"]) assert.equal(environmentSwitchPath(`/${resource}/old-id`), `/${resource}`);
  for (const path of ["/pricing", "/usage", "/events", "/settings", "/members"]) assert.equal(environmentSwitchPath(path), path);
  assert.equal(environmentSwitchPath("/admin/users"), "/customers");
});

test("tenant navigation remains pinned to the rendered organization and environment", () => {
  for (const path of ["/overview", "/usage", "/events", "/event-issues", "/event-rejections", "/billing", "/invoices", "/payments/invoice-id", "/pricing", "/customers/customer-id", "/billable-metrics", "/billable-metric-prices", "/integrations/endpoint-id", "/settings", "/members", "/audit"]) {
    const url = new URL(workspaceHref(path, source), "https://console.example");
    assert.equal(url.searchParams.get("organization_id"), "rendered-organization", path);
    assert.equal(url.searchParams.get("environment"), "test", path);
  }
});

test("explicit mismatching organization and environment survive for the server guard", () => {
  const url = new URL(workspaceHref("/customers?organization_id=other-organization&environment=live#details", source), "https://console.example");
  assert.equal(url.searchParams.get("organization_id"), "other-organization");
  assert.equal(url.searchParams.get("environment"), "live");
  assert.equal(url.hash, "#details");
});

test("common, platform, invitation and public payment links receive no tenant data", () => {
  for (const path of ["/", "/login?returnTo=%2Fcustomers", "/signup", "/organizations", "/admin", "/admin/users", "/account/consents", "/organization-invitations/token", "/organization-invitations/continue", "/payment-method-registrations/token", "/invoice-links", "/invoice-links/lookup", "/health"]) {
    assert.equal(workspaceHref(path, source), path);
  }
});

test("external and lookalike paths receive no tenant data", () => {
  for (const path of ["https://external.example/customers", "//external.example/customers", "mailto:help@example.test", "#details", "/customers-public", "/payments-other", "/integrations/../../admin"]) {
    assert.equal(workspaceHref(path, source), path);
  }
});

test("legacy remains explicit and does not inherit another environment", () => {
  const url = new URL(workspaceHref("/members?status=archived", new URLSearchParams({ organization_id: "original-organization", environment: "legacy" })), "https://console.example");
  assert.equal(url.searchParams.get("environment"), "legacy");
  assert.equal(url.searchParams.get("organization_id"), "original-organization");
  assert.equal(url.searchParams.get("status"), "archived");
});

test("navigation preserves destination filters and derives the KST month from a direct range", () => {
  const url = new URL(workspaceHref("/billing?customer_id=destination-customer", new URLSearchParams({ organization_id: "rendered-organization", from: "2026-09-30T15:00:00Z" })), "https://console.example");
  assert.equal(url.searchParams.get("month"), "2026-10");
  assert.equal(url.searchParams.get("customer_id"), "destination-customer");
  assert.equal(url.searchParams.get("organization_id"), "rendered-organization");
});

test("login can return to both authorized event diagnostic screens", () => {
  for (const path of ["/event-issues?organization_id=original-organization", "/event-rejections?environment=test"]) assert.equal(safeReturnPath(path, false), path);
  assert.equal(safeReturnPath("https://external.example/event-issues", false), "/overview");
});


test("reset and total links keep tenant scope without reviving current filters", () => {
  const current = new URLSearchParams({ organization_id: "rendered-organization", environment: "test", month: "2026-09", customer_id: "old-customer", from: "2026-09-01", to: "2026-10-01" });
  for (const destination of ["/events?month=2026-10", "/invoices?month=2026-10", "/usage?month=2026-10"]) {
    const url = new URL(workspaceHref(destination, current, { inheritFilters: false }), "https://console.example");
    assert.equal(url.searchParams.get("month"), "2026-10");
    assert.equal(url.searchParams.get("organization_id"), "rendered-organization");
    assert.equal(url.searchParams.get("environment"), "test");
    for (const key of ["customer_id", "from", "to"]) assert.equal(url.searchParams.has(key), false);
  }
});

test("scope-only links preserve an explicit selected customer and range", () => {
  const url = new URL(workspaceHref("/events?customer_id=selected&from=2026-10-01&to=2026-11-01", source, { inheritFilters: false }), "https://console.example");
  assert.equal(url.searchParams.get("customer_id"), "selected");
  assert.equal(url.searchParams.get("from"), "2026-10-01");
  assert.equal(url.searchParams.get("to"), "2026-11-01");
  assert.equal(url.searchParams.has("month"), false);
});

test("workflow navigation can explicitly inherit the current period and customer", () => {
  const url = new URL(workspaceHref("/billing", source, { inheritFilters: true }), "https://console.example");
  assert.equal(url.searchParams.get("month"), "2026-10");
  assert.equal(url.searchParams.get("customer_id"), "rendered-customer");
  assert.equal(url.searchParams.get("organization_id"), "rendered-organization");
  assert.equal(url.searchParams.get("environment"), "test");
});
