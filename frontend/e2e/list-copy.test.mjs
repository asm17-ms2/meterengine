import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = path => readFileSync(new URL(`../src/components/${path}`, import.meta.url), "utf8");

test("paged tables name their totals as totals of the whole filtered set", () => {
  const billing = source("billing/BillingTable.tsx");
  const invoices = source("invoices/InvoicesTable.tsx");
  assert.ok(billing.includes('"조건 전체 합계"') && billing.includes('"조건 전체 공급가액"'));
  assert.ok(invoices.includes(">조건 전체 합계<") && invoices.includes(">조건 전체 세금 포함 합계<"));
  for (const table of [billing, invoices]) assert.ok(!/[">]합계[<"]/.test(table) && !table.includes("공급가액 합계") && !table.includes(">세금 포함 합계<"));
});

test("an empty invoice month points to the draft amounts instead of a reset that changes nothing", () => {
  const section = source("invoices/InvoicesSection.tsx");
  assert.ok(section.includes('resetLabel="청구 예정액 확인"'));
  assert.ok(section.includes('isAllMonths ? "/billing" : `/billing?month=${month}`'));
});
