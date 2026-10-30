import assert from "node:assert/strict";
import { test } from "node:test";
import { currentBillingMonth, isBillingMonth, loadCustomerPortal } from "./customer-portal.ts";

const CUSTOMER_ID = "selected-customer";
const OTHER_CUSTOMER_ID = "other-customer";

function client(): Parameters<typeof loadCustomerPortal>[0] {
  return {
    getCustomer: async () => ({ id: CUSTOMER_ID, name: "고객", version: 0, billing_email: null, created_at: "2026-10-01T00:00:00Z" }),
    aggregateBillableMetricUsages: async () => ({ month: "2026-10", billable_metric_usages: [{ code: "tokens", name: "토큰", event_type: "usage", aggregation: "sum", target_property: "quantity", customers: [{ customer_id: CUSTOMER_ID, customer_name: "고객", quantity: 123, dimension_usages: [] }, { customer_id: OTHER_CUSTOMER_ID, customer_name: "secret-other-name", quantity: 456, dimension_usages: [] }] }] }),
    previewDraftInvoice: async () => ({ month: "2026-10", calculated_at: "2026-10-26T00:00:00Z", total_amount: 999, customers: [{ customer_id: CUSTOMER_ID, customer_name: "고객", amount: 100, lines: [], unpriced_usages: [], negative_usages: [] }, { customer_id: OTHER_CUSTOMER_ID, customer_name: "secret-other-name", amount: 899, lines: [], unpriced_usages: [], negative_usages: [] }] }),
    listInvoices: async () => ({ invoices: [{ id: "invoice-one", invoice_number: "ME-202610-00000001", customer_id: CUSTOMER_ID, customer_name: "고객", month: "2026-10", finalized_at: "2026-10-01T00:00:00Z", supply_amount: 100, tax_amount: 10, total_amount: 110, lines: [] }, { id: "invoice-other", invoice_number: "ME-202610-00000002", customer_id: OTHER_CUSTOMER_ID, customer_name: "secret-other-name", month: "2026-10", finalized_at: "2026-10-01T00:00:00Z", supply_amount: 900, tax_amount: 90, total_amount: 990, lines: [] }] }),
    listInvoicePayments: async () => ({ invoice_payments: [{ invoice_id: "invoice-one", status: "paid", mode: "test", amount: 110, canceled_amount: 0, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: "2026-10-01T00:00:00Z" }, { invoice_id: "invoice-other", status: "paid", mode: "test", amount: 990, canceled_amount: 0, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: "2026-10-01T00:00:00Z" }] }),
    getPaymentMethod: async () => ({ customer_id: CUSTOMER_ID, card_issuer_code: "88", card_number: "1234****5678", mode: "test", authenticated_at: "2026-10-01T00:00:00Z" }),
  };
}

test("도입사 서버가 조회한 전체 사용량에서도 선택한 고객의 정보만 화면에 전달한다", async () => {
  const data = await loadCustomerPortal(client(), CUSTOMER_ID, "2026-10");
  assert.deepEqual(data.usages.map((usage) => [usage.code, usage.quantity]), [["tokens", 123]]);
  assert.equal(data.draft?.amount, 100);
  assert.equal(data.invoices.length, 1);
  assert.equal(data.payments.length, 1);
  assert.equal(JSON.stringify(data).includes(OTHER_CUSTOMER_ID), false);
  assert.equal(JSON.stringify(data).includes("secret-other-name"), false);
});

test("결제 조회가 실패해도 읽은 사용량은 보존하고 금액을 0원으로 확정하지 않는다", async () => {
  const data = await loadCustomerPortal({ ...client(), listInvoicePayments: async () => { throw new Error("결제 조회 실패"); } }, CUSTOMER_ID, "2026-10");
  assert.equal(data.usages[0]?.quantity, 123);
  assert.equal(data.hasPaymentData, false);
  assert.equal(data.hasInvoiceData, true);
  assert.match(data.errors[0] ?? "", /납부 상태/);
});

test("등록 카드 없음과 카드 조회 장애를 다른 상태로 보존한다", async () => {
  const missing = await loadCustomerPortal({ ...client(), getPaymentMethod: async () => { throw Object.assign(new Error("등록 카드 없음"), { code: "payment_method_not_found" }); } }, CUSTOMER_ID, "2026-10");
  assert.equal(missing.paymentMethod, null);
  assert.equal(missing.hasPaymentMethodData, true);
  assert.deepEqual(missing.errors, []);
  const failed = await loadCustomerPortal({ ...client(), getPaymentMethod: async () => { throw new Error("시간 초과"); } }, CUSTOMER_ID, "2026-10");
  assert.equal(failed.hasPaymentMethodData, false);
  assert.match(failed.errors[0] ?? "", /등록 카드/);
});

test("청구 월은 서울 시각의 달이고 0000년이나 잘못된 월은 받지 않는다", () => {
  assert.equal(currentBillingMonth(new Date("2026-10-31T15:00:00Z")), "2026-11");
  assert.equal(isBillingMonth("2026-10"), true);
  assert.equal(isBillingMonth("0000-01"), false);
  assert.equal(isBillingMonth("2026-13"), false);
});
