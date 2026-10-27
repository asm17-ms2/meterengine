import assert from "node:assert/strict";
import test from "node:test";
import { normalizeResourceQuery, searchParametersFromUrl } from "../src/lib/resource-query.ts";

test("pricing screen and CSV share the metric archive scope, not plan history status", () => {
  const screen = { metric_status: "archived", status: "all", q: "tokens", customer_id: "customer", sort: "rate_count", order: "desc" };
  const exported = searchParametersFromUrl(new URLSearchParams(screen));
  assert.deepEqual(normalizeResourceQuery("billable-metric-prices", screen), normalizeResourceQuery("billable-metric-prices", exported));
  assert.equal(normalizeResourceQuery("billable-metric-prices", exported).status, "archived");
});

test("pricing all and active selections remain equal between screen and CSV", () => {
  for (const status of ["all", "active", "archived"]) {
    const query = { metric_status: status, month: "2026-10" };
    assert.equal(normalizeResourceQuery("billable-metric-prices", query).status, status);
  }
});

test("missing metric status does not inherit unrelated archive history status", () => {
  assert.equal(normalizeResourceQuery("billable-metric-prices", { status: "archived", plan_status: "all" }).status, "active");
});

test("duplicate and invalid metric status use the same safe default on screen and export", () => {
  const url = new URLSearchParams("metric_status=archived&metric_status=all&status=archived");
  const screen = { metric_status: ["archived", "all"], status: "archived" };
  assert.deepEqual(normalizeResourceQuery("billable-metric-prices", screen), normalizeResourceQuery("billable-metric-prices", searchParametersFromUrl(url)));
  assert.equal(normalizeResourceQuery("billable-metric-prices", screen).status, "active");
  assert.equal(normalizeResourceQuery("billable-metric-prices", { metric_status: "unknown" }).status, "active");
});

test("other resources retain their independent status and all filter values", () => {
  const query = { status: "archived", q: "customer", from: "2026-10-01T00:00:00", to: "2026-11-01T00:00:00", min_amount: "100", page: "2" };
  for (const resource of ["customers", "billable-metrics", "usage", "billing", "invoices"]) assert.deepEqual(normalizeResourceQuery(resource, query), query);
});

test("CSV pins the rendered organization even after a shared session changes", async () => {
  const { csvDownloadParameters } = await import("../src/lib/resource-query.ts");
  const result = csvDownloadParameters("billable-metric-prices", new URLSearchParams("organization_id=other&environment=legacy&metric_status=archived&plan_status=all&rate_status=archived&metric=chosen&q=tokens&page=3&size=50"), "rendered-organization");
  assert.equal(result.get("organization_id"), "rendered-organization");
  assert.equal(result.get("environment"), "legacy");
  assert.equal(result.get("metric_status"), "archived");
  assert.equal(result.get("plan_status"), "all");
  assert.equal(result.get("rate_status"), "archived");
  assert.equal(result.get("metric"), "chosen");
  assert.equal(result.get("q"), "tokens");
  assert.equal(result.has("page"), false);
  assert.equal(result.has("size"), false);
});

test("CSV rejects ambiguous repeated filters through the same screen normalization", async () => {
  const { csvDownloadParameters } = await import("../src/lib/resource-query.ts");
  const result = csvDownloadParameters("billable-metric-prices", new URLSearchParams("metric_status=archived&metric_status=all&organization_id=other&organization_id=another"), "rendered-organization");
  assert.equal(result.get("metric_status"), "active");
  assert.deepEqual(result.getAll("organization_id"), ["rendered-organization"]);
});


test("CSV preserves the screen first-value semantics for repeated filter parameters", async () => {
  const { csvDownloadParameters } = await import("../src/lib/resource-query.ts");
  const result = csvDownloadParameters("customers", new URLSearchParams("environment=test&environment=live&q=first&q=second&customer_id=first-customer&customer_id=second-customer&status=archived&status=active"), "rendered-organization");
  assert.equal(result.get("environment"), "test");
  assert.equal(result.get("q"), "first");
  assert.equal(result.get("customer_id"), "first-customer");
  assert.equal(result.get("status"), "archived");
  assert.equal(result.get("organization_id"), "rendered-organization");
});


test("submitted filters combine all fields, retain scope, and reset pagination", async () => {
  const { submittedResourceParameters } = await import("../src/lib/resource-query.ts");
  const fields = new FormData();
  for (const [name, value] of Object.entries({ environment: "test", organization_id: "rendered-organization", month: "2026-10", q: "  고객 & 토큰  ", customer_id: "customer-2", billable_metric_code: "tokens", status: "all", min_amount: "0", max_amount: "10000", sort: "amount", order: "desc", size: "50", page: "7", plan_status: "archived", rate_status: "all", metric: "first-metric" })) fields.append(name, value);
  const query = submittedResourceParameters(fields);
  for (const [key, value] of Object.entries({ environment: "test", organization_id: "rendered-organization", month: "2026-10", q: "고객 & 토큰", customer_id: "customer-2", billable_metric_code: "tokens", status: "all", min_amount: "0", max_amount: "10000", sort: "amount", order: "desc", size: "50", plan_status: "archived", rate_status: "all", metric: "first-metric" })) assert.equal(query.get(key), value, key);
  assert.equal(query.has("page"), false);
});

test("explicit invoice time ranges replace month without dropping other draft conditions", async () => {
  const { submittedResourceParameters } = await import("../src/lib/resource-query.ts");
  const query = submittedResourceParameters(new URLSearchParams({ month: "2026-10", from: "2026-10-01T12:30:00", to: "2026-10-31T18:00:00", q: "invoice", min_amount: "100", customer_id: "customer", billable_metric_code: "tokens", payment_status: "unpaid", mode: "test" }));
  assert.equal(query.has("month"), false);
  assert.equal(query.get("from"), "2026-10-01T12:30:00");
  assert.equal(query.get("to"), "2026-10-31T18:00:00");
  for (const key of ["q", "min_amount", "customer_id", "billable_metric_code", "payment_status", "mode"]) assert.equal(query.has(key), true, key);
});

test("selecting a pricing detail applies draft inputs and replaces only the selected metric", async () => {
  const { submittedResourceParameters } = await import("../src/lib/resource-query.ts");
  const fields = new FormData();
  for (const [key, value] of Object.entries({ organization_id: "rendered-organization", environment: "legacy", q: "typed before selection", customer_id: "new-customer", month: "2026-11", plan_status: "active", rate_status: "archived", metric: "old-metric", size: "100" })) fields.append(key, value);
  fields.append("plan_status", "all");
  const query = submittedResourceParameters(fields, { name: "metric", value: "new-metric" });
  assert.deepEqual(query.getAll("metric"), ["new-metric"]);
  assert.equal(query.get("q"), "typed before selection");
  assert.equal(query.get("customer_id"), "new-customer");
  assert.equal(query.get("month"), "2026-11");
  assert.equal(query.get("plan_status"), "all");
  assert.equal(query.get("rate_status"), "archived");
  assert.equal(query.get("size"), "100");
  assert.equal(query.get("organization_id"), "rendered-organization");
});

test("clearing controls removes their values while preserving explicit zero amounts", async () => {
  const { submittedResourceParameters } = await import("../src/lib/resource-query.ts");
  const query = submittedResourceParameters(new URLSearchParams({ q: " ", customer_id: "", billable_metric_code: "", payment_status: "", min_amount: "0", mode: "", from: "", to: "", month: "2026-10" }));
  assert.deepEqual(Object.fromEntries(query), { min_amount: "0", month: "2026-10" });
});


test("data month shortcuts apply current draft fields and clear the previous explicit range", async () => {
  const { submittedResourceParameters } = await import("../src/lib/resource-query.ts");
  const query = submittedResourceParameters(new URLSearchParams({ organization_id: "rendered-organization", environment: "legacy", month: "2026-10", from: "2026-10-01T00:00:00", to: "2026-10-08T00:00:00", range_mode: "date", q: "typed query", min_amount: "0", customer_id: "customer" }), { name: "month", value: "2026-09" });
  assert.equal(query.get("month"), "2026-09");
  for (const key of ["from", "to", "range_mode", "page"]) assert.equal(query.has(key), false, key);
  for (const [key, value] of Object.entries({ organization_id: "rendered-organization", environment: "legacy", q: "typed query", min_amount: "0", customer_id: "customer" })) assert.equal(query.get(key), value, key);
});

test("event CSV exports the complete applied range while retaining scope and exact property JSON", async () => {
  const { csvDownloadParameters } = await import("../src/lib/resource-query.ts");
  const values = { environment: "test", from: "2026-09-30T23:59:10", to: "2026-10-01T00:00:13", range_mode: "datetime", customer_id: "selected-customer", type: "llm.response", q: "한글 / 전송", properties: '{"tokens":12345678901234567890,"enabled":true}', sort: "transaction_id", order: "asc", include_properties: "true" };
  const query = csvDownloadParameters("events", new URLSearchParams({ ...values, organization_id: "other-session-organization", page: "4", size: "50" }), "rendered-organization");
  for (const [key, value] of Object.entries(values)) assert.equal(query.get(key), value, key);
  assert.equal(query.get("organization_id"), "rendered-organization");
  assert.equal(query.has("page"), false);
  assert.equal(query.has("size"), false);
});

test("event CSV follows the applied first-value filters without silently enabling properties", async () => {
  const { csvDownloadParameters } = await import("../src/lib/resource-query.ts");
  const query = csvDownloadParameters("events", new URLSearchParams("month=2026-10&type=selected&type=other&q=first&q=second&properties=%7B%22enabled%22%3Atrue%7D&properties=%7B%7D&page=2&size=100"), "rendered-organization");
  assert.equal(query.get("month"), "2026-10");
  assert.equal(query.get("type"), "selected");
  assert.equal(query.get("q"), "first");
  assert.equal(query.get("properties"), '{"enabled":true}');
  assert.equal(query.has("include_properties"), false);
  assert.equal(query.has("page"), false);
  assert.equal(query.has("size"), false);
});
