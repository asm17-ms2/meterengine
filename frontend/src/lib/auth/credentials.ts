export const NAME_MAX_LENGTH = 255;
export const EMAIL_MAX_LENGTH = 254;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_BYTES = 72;
export const PASSWORD_RULE_MESSAGE = "비밀번호는 8자 이상 72자 이하로 입력하세요. 한글은 1자를 3자로 계산합니다.";

export function isValidPassword(password: string): boolean {
  return password.length >= PASSWORD_MIN_LENGTH && new TextEncoder().encode(password).length <= PASSWORD_MAX_BYTES;
}

export function isValidEmail(email: string): boolean {
  return email.length <= EMAIL_MAX_LENGTH && /^\S+@\S+\.\S+$/.test(email);
}

// 이름과 이메일은 넘긴 것만 검사한다. 화면 필드 이름을 키로 돌려준다.
export function credentialFieldErrors(input: { name?: string; email?: string; password: string }): Record<string, string> {
  const errors: Record<string, string> = {};
  if (input.name !== undefined) {
    if (!input.name) errors.name = "이름을 입력하세요.";
    else if (input.name.length > NAME_MAX_LENGTH) errors.name = "이름은 255자 이내로 입력하세요.";
  }
  if (input.email !== undefined && !isValidEmail(input.email)) errors.email = "올바른 이메일 주소를 입력하세요.";
  if (!isValidPassword(input.password)) errors.password = PASSWORD_RULE_MESSAGE;
  return errors;
}
