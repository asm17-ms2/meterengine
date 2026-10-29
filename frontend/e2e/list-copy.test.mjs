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

test("mobile invoice cards show the invoice number and the same payment link as the desktop table", () => {
  const invoices = source("invoices/InvoicesTable.tsx");
  const mobile = invoices.slice(invoices.indexOf('className="data-mobile-records"'));
  assert.ok(mobile.includes("group.invoiceNumber"));
  assert.ok(mobile.includes("<InvoicePaymentLink"));
  assert.equal(invoices.match(/수납과 고객 조회 링크/g)?.length, 1);
});

test("a truncated customer label offers the full name and id", () => {
  const label = source("table/CustomerLabel.tsx");
  assert.ok(label.includes("ellipsis={{ tooltip: `${name} (${id})` }}"));
});

test("the usage quantity header and sort option say they sort by aggregated line count", () => {
  const usage = source("usage/UsageTable.tsx");
  const filters = source("screen/ResourceSearchControls.tsx");
  assert.ok(usage.includes('showSorterTooltip: { title: "집계 라인 수로 정렬" }'));
  assert.ok(filters.includes('{ value: "line_count", label: "집계 라인 수" }'));
});

test("the meter search empty state counts all meters and the sort note follows the applied sort", () => {
  const screen = source("billable-metrics/BillableMetricsScreen.tsx");
  const section = source("billable-metrics/BillableMetricsSection.tsx");
  assert.ok(section.includes("totalUnfiltered={result.data.total_unfiltered}"));
  assert.ok(screen.includes("등록된 미터는 ${totalUnfiltered ?? rows.length}개입니다."));
  assert.ok(!screen.includes("<ScreenNote>정렬: 코드 오름차순</ScreenNote>"));
  assert.ok(screen.includes('query.sort ?? "code"'));
});

test("the meter screen shows the same submit-based archive filter while loading or failing", () => {
  const frame = source("billable-metrics/BillableMetricsFrame.tsx");
  assert.ok(frame.includes('<ResourceSearchControls query={query} capabilities={capabilities} resource="billable-metrics" />'));
  assert.ok(!frame.includes("<ListControls"));
});

