const SUCCESS_MESSAGES: Partial<Record<string, string>> = {
  createMember: "사용자 계정을 만들었습니다.",
  updateMemberRole: "사용자 역할을 바꿨습니다.",
  updateMemberScopes: "사용자 권한을 저장했습니다.",
  revokeOrganizationInvitation: "초대를 취소했습니다.",
  deleteApiKey: "API 키를 폐기했습니다.",
  updateApiKeyScopes: "API 키 권한을 저장했습니다.",
};

export function successMessage(operation: string): string {
  return SUCCESS_MESSAGES[operation] ?? "처리했습니다.";
}

const UNCONFIRMED_MESSAGES: Partial<Record<string, string>> = {
  updateTossPaymentsCredential: "토스 키 저장 결과를 확인하지 못했습니다. 이 화면을 새로 조회해 토스페이먼츠 연동 목록의 저장 시각을 확인하고, 바뀌지 않았으면 다시 저장하세요.",
};

export function unconfirmedMessage(operation: string): string {
  return UNCONFIRMED_MESSAGES[operation] ?? "처리 결과를 확인하지 못했습니다. 중복 요청을 피하려면 결제와 취소 내역을 먼저 새로 조회하세요.";
}

const ERROR_MESSAGES: Partial<Record<string, string>> = {
  toss_payments_credential_missing: "현재 결제 환경의 토스페이먼츠 키가 없습니다. 도입사 설정의 토스페이먼츠 연동에서 키를 저장한 뒤 다시 시도하세요.",
};

export function errorMessageForCode(code: string): string | undefined {
  return ERROR_MESSAGES[code];
}

export function staleCancellationMessage(operation: string, code?: string): string | undefined {
  if (operation !== "createPaymentCancellation") return undefined;
  if (code === "payment_cancellation_idempotency_conflict") return unconfirmedMessage(operation);
  if (code === "payment_attempt_fully_canceled" || code === "payment_cancellation_amount_exceeded") return "다른 취소가 먼저 처리되어 화면의 잔액이 최신이 아닙니다. 이번 요청은 처리되지 않았습니다. 결제와 취소 내역을 새로 조회한 뒤 다시 요청하세요.";
  return undefined;
}

export class InputFieldError extends Error {
  readonly field: string;

  constructor(field: string, message: string) {
    super(message);
    this.field = field;
  }
}

export function readWholeNumber(value: string, field: string, { min, max, message }: { min: number; max: number; message: string }): number {
  const number = Number(value);
  if (!/^\d+$/.test(value) || !Number.isSafeInteger(number) || number < min || number > max) throw new InputFieldError(field, message);
  return number;
}

export function readBusinessRegistrationNumber(value: string, field: string): string {
  const digits = value.replace(/[\s-]/g, "");
  if (!/^\d{10}$/.test(digits)) throw new InputFieldError(field, "사업자등록번호는 숫자 10자리로 입력하세요.");
  return digits;
}
