import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = path => readFileSync(new URL(`../src/components/${path}`, import.meta.url), "utf8");
const appSource = path => readFileSync(new URL(`../src/app/${path}`, import.meta.url), "utf8");

test("paged tables name their totals as totals of the whole filtered set", () => {
  const billing = source("billing/BillingTable.tsx");
  const invoices = source("invoices/InvoicesTable.tsx");
  assert.ok(billing.split('"조건 전체 공급가액"').length === 3 && billing.split('"선택 고객 공급가액"').length === 3 && !billing.includes("조건 전체 합계"));
  assert.ok(invoices.split(">조건 전체 합계(부가세 포함)<").length === 3 && !invoices.includes("조건 전체 세금 포함 합계"));
  for (const table of [billing, invoices]) assert.ok(!/[">]합계[<"]/.test(table) && !table.includes("공급가액 합계") && !table.includes(">세금 포함 합계<"));
});

test("an empty invoice month points to the draft amounts instead of a reset that changes nothing", () => {
  const section = source("invoices/InvoicesSection.tsx");
  assert.ok(section.includes('resetLabel="청구 검토로 이동"'));
  assert.ok(section.includes('isAllMonths ? "/billing" : `/billing?month=${month}`'));
});

test("mobile invoice cards show the invoice number and the same payment link as the desktop table", () => {
  const invoices = source("invoices/InvoicesTable.tsx");
  const mobile = invoices.slice(invoices.indexOf('className="data-mobile-records"'));
  assert.ok(mobile.includes("group.invoiceNumber"));
  assert.ok(mobile.includes("<InvoicePaymentLink"));
  assert.equal(invoices.match(/수납과 청구서 조회 링크/g)?.length, 1);
});

test("a truncated customer label offers the full name and id", () => {
  const label = source("table/CustomerLabel.tsx");
  assert.ok(label.includes("ellipsis={{ tooltip: `${name} (${id})` }}"));
});

// 집계 수량 열 머리는 정렬하지 않고, 고객별 줄 수 정렬은 정렬 선택지에만 둔다(결정 15).
test("the usage quantity header does not sort and the line count sort stays in the sort options", () => {
  const usage = source("usage/UsageTable.tsx");
  const filters = source("screen/ResourceSearchControls.tsx");
  const quantityColumn = usage.slice(usage.indexOf('title: "집계 수량"'), usage.indexOf("render:", usage.indexOf('title: "집계 수량"')));
  assert.ok(quantityColumn.length > 0);
  for (const sorter of ["clientSorter", "sortKey", "showSorterTooltip"]) assert.ok(!quantityColumn.includes(sorter), sorter);
  assert.ok(filters.includes('{ value: "line_count", label: "집계 항목 수" }'));
});

test("the meter search empty state counts all meters and the screen leaves the sort to the filter row", () => {
  const screen = source("billable-metrics/BillableMetricsScreen.tsx");
  const section = source("billable-metrics/BillableMetricsSection.tsx");
  assert.ok(section.includes("totalUnfiltered={result.data.total_unfiltered}"));
  assert.ok(screen.includes("등록된 미터는 ${totalUnfiltered ?? rows.length}개예요."));
  assert.ok(!screen.includes("<ScreenNote>"));
});

test("the meter screen shows the same submit-based archive filter while loading or failing", () => {
  const frame = source("billable-metrics/BillableMetricsFrame.tsx");
  assert.ok(frame.includes('<ResourceSearchControls query={query} capabilities={capabilities} resource="billable-metrics" />'));
  assert.ok(!frame.includes("<ListControls"));
});

test("the event detail drawer uses the table's Korean labels and says the raw JSON is in UTC", () => {
  const drawer = source("events/EventDetailDrawer.tsx");
  for (const label of ['label: "전송 ID"', 'label: "이벤트 유형"', 'label: "발생 시각 (KST)"', 'label: "수신 시각 (KST)"']) assert.ok(drawer.includes(label), label);
  for (const raw of ['label: "transaction_id"', 'label: "type"', 'label: "occurred_at"', 'label: "received_at"']) assert.ok(!drawer.includes(raw), raw);
  assert.ok(drawer.includes("API 원본 값, 시각은 UTC"));
});

test("mobile event links open the drawer in place and closing it returns focus to the row link", () => {
  const table = source("events/EventsTable.tsx");
  const routed = source("events/RoutedEventDetail.tsx");
  assert.ok(table.includes("data-transaction-id={row.transactionId}"));
  assert.equal(table.match(/onClick=\{event => openOnPlainClick\(event, row\)\}/g)?.length, 1);
  assert.ok(table.includes("<a href={detailHref(row)} data-transaction-id={row.transactionId} onClick={event => openOnPlainClick(event, row)}>"));
  assert.ok(routed.includes("data-transaction-id"));
});

test("the rejection search keeps its customer id guidance visible below the search row and tied to the field", () => {
  const page = readFileSync(new URL("../src/app/(console)/event-rejections/page.tsx", import.meta.url), "utf8");
  assert.ok(!page.includes('placeholder="존재하지 않는 고객 ID도 조회할 수 있습니다"'));
  assert.ok(page.includes('aria-describedby="rejection-customer-hint"'));
  assert.ok(page.includes('<p id="rejection-customer-hint" className="field-hint">요청 본문의 고객 ID(UUID)로 찾아요. 등록되지 않은 ID로도 찾을 수 있어요.</p>'));
});

test("usage and event screens drop the mismatched sub-tab rows and keep their cross links in the content", () => {
  const usage = readFileSync(new URL("../src/app/(console)/usage/page.tsx", import.meta.url), "utf8");
  const events = readFileSync(new URL("../src/app/(console)/events/page.tsx", import.meta.url), "utf8");
  for (const page of [usage, events]) assert.ok(!page.includes('className="workspace-tabs"'));
  assert.ok(usage.includes('<LinkButton inheritFilters href="/billing">이 달 청구 금액 보기</LinkButton>'));
  assert.ok(events.includes('<ScopedLink href="/integrations">API 키와 웹훅</ScopedLink>'));
});

test("mobile usage cards label each dimension row with its dimension values under one group heading", () => {
  const usage = source("usage/UsageTable.tsx");
  assert.ok(!usage.includes('item.kind === "meter" ? "구분 값별 집계값" : line.label'));
  assert.ok(usage.includes("{toDimensionText(line.dimensionValues)}"));
  assert.ok(usage.includes('<p className="field-hint">구분 값별 집계값</p>'));
});

test("the event issue summary uses the same reason names as the filter and the problem column", () => {
  const issues = appSource("(console)/event-issues/page.tsx");
  assert.ok(!issues.includes("미터 없음 {result.data.summary"));
  assert.ok(issues.includes("{REASONS.unknown_event_type} {result.data.summary.unknown_event_type}건"));
  assert.ok(issues.includes("{REASONS.unpriced_usage} {result.data.summary.unpriced_usage}건"));
});

test("the overview unpriced usage item also links to events filtered to a missing price", () => {
  const overview = appSource("(console)/overview/page.tsx");
  assert.ok(overview.includes("<Link href={`/event-issues?month=${month}&reason=unpriced_usage`}>이벤트 보기</Link>"));
});
