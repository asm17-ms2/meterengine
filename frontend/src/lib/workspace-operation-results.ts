export type OperationResultCopy = { refreshLabel: string; retryLabel: string; linkLabel: string; linkNote: string };

const DEFAULT_COPY: OperationResultCopy = { refreshLabel: "최신 상태 새로 조회", retryLabel: "새 요청", linkLabel: "링크", linkNote: "한 번 사용할 수 있는 링크입니다. 닫기 전에 복사하세요." };
const PAYMENT_REFRESH = "결제와 취소 내역 새로 조회";

const COPY: Partial<Record<string, Partial<OperationResultCopy>>> = {
  createPaymentAttempt: { refreshLabel: PAYMENT_REFRESH },
  createPaymentCancellation: { refreshLabel: PAYMENT_REFRESH, retryLabel: "새 취소 요청" },
  updateTossPaymentsCredential: { refreshLabel: "토스 키 상태 새로 조회" },
  createPaymentMethodRegistration: { linkLabel: "카드 등록 링크", linkNote: "24시간 동안 한 번 사용할 수 있는 카드 등록 링크입니다." },
  createOrganizationInvitation: { linkLabel: "초대 링크", linkNote: "한 번 사용할 수 있는 초대 링크입니다. 닫기 전에 복사하고 초대할 사람에게 안전하게 전달하세요." },
};

export function operationResultCopy(operation: string): OperationResultCopy {
  return { ...DEFAULT_COPY, ...COPY[operation] };
}
