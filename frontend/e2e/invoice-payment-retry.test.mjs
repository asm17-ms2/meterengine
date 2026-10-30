import assert from "node:assert/strict";
import test from "node:test";
import { canRetryInvoicePayment, invoicePaymentRetryUnavailableMessage, isValidInvoiceRetryVersion } from "../src/lib/invoice-payment-retry.ts";

function invoice(overrides = {}) {
  return {
    total_amount: 1100,
    payment: { status: "failed", mode: "test", amount: 1100, canceled_amount: 0 },
    payment_retry: { allowed: true, reason: "available", version: "a".repeat(64) },
    ...overrides,
  };
}

test("실패 결제의 별도 권한과 현재 금액을 모두 확인해야 고객 재시도를 제공한다", () => {
  assert.equal(canRetryInvoicePayment(invoice()), true);
  assert.equal(canRetryInvoicePayment(invoice({ payment: { ...invoice().payment, mode: "live" } })), true);
  assert.equal(canRetryInvoicePayment(invoice({ payment_retry: { allowed: false, reason: "link_read_only", version: null } })), false);
  assert.equal(canRetryInvoicePayment(invoice({ payment_retry: undefined })), false);
  assert.equal(canRetryInvoicePayment(invoice({ payment_retry: { allowed: true, reason: "unknown", version: "a".repeat(64) } })), false);
});

test("첫 결제와 결과 미확인, 납부, 취소 상태에서는 재시도 버튼을 제공하지 않는다", () => {
  for (const status of ["unpaid", "pending", "paid", "partially_canceled", "canceled", "not_required", "unknown"]) {
    assert.equal(canRetryInvoicePayment(invoice({ payment: { ...invoice().payment, status } })), false, status);
  }
  assert.equal(canRetryInvoicePayment(invoice({ payment: { ...invoice().payment, canceled_amount: 1 } })), false);
});

test("금액과 모드를 확인하지 못하면 재시도를 차단한다", () => {
  for (const amount of [0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) {
    assert.equal(canRetryInvoicePayment(invoice({ total_amount: amount, payment: { ...invoice().payment, amount } })), false);
  }
  assert.equal(canRetryInvoicePayment(invoice({ payment: { ...invoice().payment, amount: 1099 } })), false);
  assert.equal(canRetryInvoicePayment(invoice({ payment: { ...invoice().payment, mode: "unknown" } })), false);
});

test("재시도 기준은 서버가 발급한 64자리 소문자 hex 형식만 허용한다", () => {
  assert.equal(isValidInvoiceRetryVersion("0123456789abcdef".repeat(4)), true);
  for (const version of [null, "", "a".repeat(63), "a".repeat(65), "A".repeat(64), "a".repeat(64) + "\n", "me_invoice_" + "a".repeat(32)]) {
    assert.equal(isValidInvoiceRetryVersion(version), false);
    assert.equal(canRetryInvoicePayment(invoice({ payment_retry: { allowed: true, reason: "available", version } })), false);
  }
});

test("재시도 중지 이유를 고객이 할 수 있는 행동으로 안내하고 서버 원문은 노출하지 않는다", () => {
  assert.match(invoicePaymentRetryUnavailableMessage("card_update_required"), /카드 등록 방법/);
  assert.match(invoicePaymentRetryUnavailableMessage("retry_limit_reached"), /횟수/);
  assert.match(invoicePaymentRetryUnavailableMessage("payment_result_unconfirmed"), /결제 상태를 새로 조회/);
  assert.equal(invoicePaymentRetryUnavailableMessage("unrecognized-secret-code").includes("unrecognized-secret-code"), false);
});
