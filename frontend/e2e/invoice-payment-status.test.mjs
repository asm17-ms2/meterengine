import assert from "node:assert/strict";
import test from "node:test";
import { INVOICE_PAYMENT_STATUS_LABELS, INVOICE_PAYMENT_STATUSES } from "../src/lib/invoice-payment-status.ts";

test("each invoice payment status has one name used by both the list and the detail", () => {
  const codes = ["not_required", "unpaid", "pending", "paid", "failed", "partially_canceled", "canceled"];
  for (const code of codes) assert.ok(INVOICE_PAYMENT_STATUSES[code].label && INVOICE_PAYMENT_STATUSES[code].color, code);
  assert.deepEqual([INVOICE_PAYMENT_STATUS_LABELS.unpaid, INVOICE_PAYMENT_STATUS_LABELS.pending, INVOICE_PAYMENT_STATUS_LABELS.failed, INVOICE_PAYMENT_STATUS_LABELS.paid], ["결제 전", "결과 확인 중", "결제 실패", "수납 완료"]);
  const labels = codes.map(code => INVOICE_PAYMENT_STATUS_LABELS[code]);
  assert.equal(new Set(labels).size, labels.length);
  for (const label of labels) assert.ok(!["미청구", "미납", "납부 완료"].includes(label), label);
});
