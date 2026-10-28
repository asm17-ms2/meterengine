export type FieldValidity = Pick<ValidityState, "valueMissing" | "typeMismatch" | "rangeUnderflow" | "rangeOverflow" | "stepMismatch" | "badInput" | "tooLong" | "valid">;

export function fieldValidationMessage(validity: FieldValidity, field: { type?: string; isSelect?: boolean; min?: number; max?: number; step?: number | "any"; maxLength?: number }): string | undefined {
  if (validity.valid) return undefined;
  if (validity.valueMissing) return field.isSelect ? "항목을 선택하세요." : "값을 입력하세요.";
  if (validity.typeMismatch) return field.type === "email" ? "이메일 주소 형식으로 입력하세요." : field.type === "url" ? "URL 형식으로 입력하세요." : "형식을 확인하세요.";
  if (validity.rangeUnderflow) return `${field.min} 이상으로 입력하세요.`;
  if (validity.rangeOverflow) return `${field.max} 이하로 입력하세요.`;
  if (validity.stepMismatch || validity.badInput) return field.step === "any" ? "숫자로 입력하세요." : "정수로 입력하세요.";
  if (validity.tooLong) return `${field.maxLength}자 이하로 입력하세요.`;
  return "입력한 값을 확인하세요.";
}
