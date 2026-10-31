import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import test from "node:test";

registerHooks({ resolve(specifier, context, next) {
  return next(specifier.startsWith("@/") ? new URL(`../src/${specifier.slice(2)}.ts`, import.meta.url).href : specifier, context);
} });

test("old payment mode query never changes API parameters or list navigation", async () => {
  const { readListQuery, toServerParams, buildListHref } = await import("../src/lib/list-query.ts");
  const { CAPABILITIES } = await import("../src/lib/api/capabilities.ts");
  const query = readListQuery({ environment: "test", mode: "live", organization_id: "original-org", month: "2026-10" }, CAPABILITIES.listInvoices, { hasMonth: true });
  assert.equal(toServerParams(query, CAPABILITIES.listInvoices).mode, undefined);
  const href = new URL(buildListHref("/invoices", query), "https://console.example");
  assert.equal(href.searchParams.has("mode"), false);
  assert.equal(href.searchParams.get("environment"), "test");
  assert.equal(href.searchParams.get("month"), "2026-10");
});
