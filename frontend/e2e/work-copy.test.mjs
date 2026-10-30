import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = path => readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8");

test("pricing history empty states use the readable Empty component instead of the faint default text", () => {
  const pricing = source("components/pricing/PricingWorkspace.tsx");
  assert.equal(pricing.match(/emptyText: ?<Empty image=\{Empty\.PRESENTED_IMAGE_SIMPLE\}/g)?.length, 2);
});

test("pricing history money columns are right aligned numbers", () => {
  const pricing = source("components/pricing/PricingWorkspace.tsx");
  for (const column of ['{title:"기본료",dataIndex:"base_fee",align:"right"', '{title:"최소 사용료",dataIndex:"minimum_amount",align:"right"', '{title:"단가",key:"price",align:"right"']) assert.ok(pricing.includes(column), column);
});

test("unit prices say they are per billed quantity, not per raw usage", () => {
  const pricing = source("components/pricing/PricingWorkspace.tsx");
  for (const stale of ["/ 사용량 1<", "원 / 1<", '"사용량 1당 단가"', "<span>사용량 1당 단가 (원)</span>"]) assert.ok(!pricing.includes(stale), stale);
  for (const label of ["</strong> / 과금 수량 1</p>", '"과금 수량 1당 단가"', "<span>과금 수량 1당 단가 (원)</span>"]) assert.ok(pricing.includes(label), label);
});

test("the pricing CSS module does not rely on global class selectors that the module hashes away", () => {
  const css = source("components/pricing/pricing.module.css");
  assert.ok(!css.includes(".tierEditorRow>.ant-btn") && !css.includes(".tierEditorRow .workspace-field"));
  assert.ok(css.includes(".tierEditorRow label{font-size:12px}"));
});

test("the finalization result lists only the first few invoices and points to the invoice list for the rest", () => {
  const dialog = source("components/billing/InvoiceFinalizeDialog.tsx");
  assert.ok(dialog.includes("const FINALIZED_PREVIEW_COUNT = 5;"));
  assert.ok(dialog.includes("state.invoices.slice(0, FINALIZED_PREVIEW_COUNT).map("));
  assert.ok(dialog.includes("인보이스 보기'에서 확인하세요."));
});

test("the pricing meter rail stays a wrapping row of chips at 900px and below", () => {
  const css = source("components/pricing/pricing.module.css");
  assert.ok(css.includes("@media(max-width:900px){.meterRail.meterRail{flex-direction:row}.meterRail>:not(button){width:100%}}"));
});

test("a disabled month finalization shows its reason as text the button describes, not only a tooltip", () => {
  const button = source("components/billing/InvoiceFinalizeButton.tsx");
  const dialog = source("components/billing/InvoiceFinalizeDialog.tsx");
  const section = source("components/billing/InvoiceFinalizeSection.tsx");
  assert.ok(!button.includes("title={title}") && button.includes("aria-describedby={hintId}") && button.includes('className="field-hint"'));
  assert.ok(dialog.includes('disabledReason={isEmpty ? "청구할 금액이 있는 고객이 없거나 이미 모두 확정됐습니다." : undefined}'));
  assert.ok(section.includes('disabledReason="청구 예정액을 불러오지 못해 확정할 수 없습니다."'));
});

test("archive actions show their success message before moving to the archived list with the viewed month", () => {
  const actions = source("app/(console)/workspace-actions.ts");
  const panel = source("components/workspace/ActionPanel.tsx");
  const detail = source("app/(console)/customers/[id]/page.tsx");
  assert.ok(!actions.includes('redirect(workspaceHref("/customers?status=archived"'));
  assert.ok(actions.includes("nextPath: workspaceHref("));
  assert.ok(panel.includes("if (result.nextPath) router.push(result.nextPath);"));
  assert.ok(detail.includes('{ name: "month", label: "", type: "hidden" as const, value: month }'));
});


test("the customer archive action sits with the other customer actions, not in the navigation link row", () => {
  const lines = source("app/(console)/customers/[id]/page.tsx").split("\n");
  const actionRow = lines.find(line => line.includes('operation="updateCustomer"'));
  const navigationRow = lines.find(line => line.includes(">수집 로그 확인</Link>"));
  assert.ok(actionRow.includes('operation={detail.archived_at ? "restoreCustomer" : "deleteCustomer"}'));
  assert.ok(!actionRow.includes(">고객별 요금 설계</Link>"));
  assert.ok(!navigationRow.includes("<ActionPanel") && navigationRow.includes(">고객별 요금 설계</Link>"));
});

test("every pricing rail meter shows its rate count and the selected meter's formula sits in its price panel", () => {
  const pricing = source("components/pricing/PricingWorkspace.tsx");
  const page = source("app/(console)/pricing/page.tsx");
  assert.ok(pricing.includes('<small style={{ overflowWrap: "anywhere" }}>{`등록 단가 ${metric.rate_count ?? 0}개`}</small>'));
  assert.ok(pricing.includes("<p>{props.month} / {customerName}</p>{selectedMetric?.aggregation && <p>{metricCalculationFormula("));
  assert.ok(page.includes("{ ...selectedMetric.data, rate_count: metric.rate_count }"));
});

test("a meter shows both its name and code on the usage screen and on customer detail", () => {
  const usage = source("lib/api/usage.ts");
  const detail = source("app/(console)/customers/[id]/page.tsx");
  assert.ok(usage.includes("const labelTooltip = [`미터명: ${billableMetricUsage.name}`, formatTargetPropertyTooltip("));
  assert.ok(detail.includes("metric: `${metric.name} (${metric.code})`"));
});

test("customer detail and overview write months and times in the shared Korean format", () => {
  const detail = source("app/(console)/customers/[id]/page.tsx");
  const overview = source("app/(console)/overview/page.tsx");
  assert.ok(detail.includes("<h2>{formatKoreanMonth(month)} 청구와 수납</h2>"));
  assert.ok(overview.includes("{formatKstDateTime(data.calculated_at)} KST 기준."));
  assert.ok(!overview.includes("new Date(data.calculated_at).toLocaleString("));
});

test("the overview failed payment item opens the payments list filtered to failures", () => {
  const overview = source("app/(console)/overview/page.tsx");
  assert.ok(overview.includes("<Link href={`/payments?month=${month}&payment_status=failed`}>수납 확인</Link>"));
});

test("customer detail asks the server for only that customer's cards and still drops cards of other customers", () => {
  const detail = source("app/(console)/customers/[id]/page.tsx");
  assert.ok(detail.includes('"/v1/payment-methods", { status: cardStatus, customer_id: id }'));
  assert.ok(detail.includes("payment_methods.filter(method => method.customer_id === id)"));
});

test("base fee and minimum fee accept any whole won and only say the billed amount drops amounts under 10 won", () => {
  const pricing = source("components/pricing/PricingWorkspace.tsx");
  for (const name of ["base_fee", "minimum_amount"]) {
    const field = pricing.match(new RegExp(`\\{name:"${name}",[^}]*\\}`))[0];
    assert.ok(!field.includes("step:10") && field.includes("청구할 때 10원 미만은 버립니다."), field);
  }
});
