export const INVOICE_PAYMENT_STATUSES: Record<string, { label: string; color: "default" | "success" | "warning" | "error" }> = {
  not_required: { label: "결제 불필요", color: "default" },
  unpaid: { label: "결제 전", color: "default" },
  pending: { label: "결과 확인 중", color: "warning" },
  paid: { label: "결제 완료", color: "success" },
  failed: { label: "결제 실패", color: "error" },
  partially_canceled: { label: "부분 취소", color: "default" },
  canceled: { label: "전액 취소", color: "default" },
};

export const INVOICE_PAYMENT_STATUS_LABELS: Record<string, string> = Object.fromEntries(Object.entries(INVOICE_PAYMENT_STATUSES).map(([code, status]) => [code, status.label]));

// 실패 코드 가운데 MeterEngine이 붙이는 값만 한국어로 바꾸고, 토스페이먼츠 코드와 상태는 그대로 보인다.
const PAYMENT_FAILURE_CODE_LABELS: Record<string, string> = {
  toss_payments_credential_missing: "토스페이먼츠 키 없음",
  payment_method_missing: "등록 카드 없음",
};

export function paymentFailureCodeLabel(code: string | null | undefined): string | null {
  if (!code) return null;
  if (code === "NOT_FOUND_PAYMENT") return "결과 미확인";
  const httpStatus = /^HTTP_(\d+)$/.exec(code)?.[1];
  if (httpStatus) return `토스페이먼츠 응답 오류(${httpStatus})`;
  return PAYMENT_FAILURE_CODE_LABELS[code] ?? code;
}
