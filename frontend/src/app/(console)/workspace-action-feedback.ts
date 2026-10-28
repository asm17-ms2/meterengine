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
