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
