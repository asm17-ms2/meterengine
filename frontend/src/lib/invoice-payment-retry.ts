import type { PublicInvoice } from "./invoice-view-links";

const RETRY_UNAVAILABLE_MESSAGES: Record<string, string> = {
  link_read_only: "이 링크는 조회 전용입니다. 결제 재시도는 청구서를 보낸 담당자에게 문의해 주세요.",
  payment_not_failed: "결제 상태가 변경되었습니다. 결제 상태를 새로 조회해 주세요.",
  payment_mode_changed: "결제 설정이 변경되어 이 링크에서는 다시 결제할 수 없습니다. 담당자에게 새 링크를 요청해 주세요.",
  payment_method_missing: "등록된 카드를 확인해야 합니다. 담당자에게 카드 등록 방법을 안내받아 주세요.",
  credentials_missing: "현재 결제를 요청할 수 없습니다. 청구서를 보낸 담당자에게 확인을 요청해 주세요.",
  retry_limit_reached: "결제 재시도 횟수에 도달했습니다. 청구서를 보낸 담당자에게 확인을 요청해 주세요.",
  card_update_required: "등록된 카드를 다시 확인해야 합니다. 담당자에게 카드 등록 방법을 안내받아 주세요.",
  payment_result_unconfirmed: "이전 결제 결과를 확인해야 합니다. 결제 상태를 새로 조회하거나 담당자에게 확인을 요청해 주세요.",
  retry_not_allowed: "현재 이 결제를 다시 시도할 수 없습니다. 청구서를 보낸 담당자에게 확인을 요청해 주세요.",
};

export function isValidInvoiceRetryVersion(value: unknown): value is string {
  return typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
}

export function canRetryInvoicePayment(invoice: PublicInvoice): boolean {
  return invoice.payment.status === "failed"
    && invoice.payment_retry?.allowed === true
    && invoice.payment_retry.reason === "available"
    && isValidInvoiceRetryVersion(invoice.payment_retry.version)
    && ["test", "live"].includes(invoice.payment.mode)
    && Number.isSafeInteger(invoice.total_amount) && invoice.total_amount > 0
    && invoice.payment.amount === invoice.total_amount
    && invoice.payment.canceled_amount === 0;
}

export function invoicePaymentRetryUnavailableMessage(reason?: string): string {
  return RETRY_UNAVAILABLE_MESSAGES[reason ?? ""] ?? "현재 결제를 다시 시도할 수 없습니다. 결제 상태를 새로 조회하거나 청구서를 보낸 담당자에게 문의해 주세요.";
}
