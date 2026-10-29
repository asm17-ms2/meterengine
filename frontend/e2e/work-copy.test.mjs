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

