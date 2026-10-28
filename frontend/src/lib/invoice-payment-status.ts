export const INVOICE_PAYMENT_STATUSES: Record<string, { label: string; color: "default" | "success" | "warning" | "error" }> = {
  not_required: { label: "결제 불필요", color: "default" },
  unpaid: { label: "결제 전", color: "default" },
  pending: { label: "결과 확인 중", color: "warning" },
  paid: { label: "수납 완료", color: "success" },
  failed: { label: "결제 실패", color: "error" },
  partially_canceled: { label: "부분 취소", color: "default" },
  canceled: { label: "전액 취소", color: "default" },
};

export const INVOICE_PAYMENT_STATUS_LABELS: Record<string, string> = Object.fromEntries(Object.entries(INVOICE_PAYMENT_STATUSES).map(([code, status]) => [code, status.label]));
