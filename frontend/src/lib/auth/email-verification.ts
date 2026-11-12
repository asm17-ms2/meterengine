// 백엔드 EmailVerificationService가 만드는 토큰 형식이다.
const TOKEN_PATTERN = /^me_verify_[A-Za-z0-9]{32}$/;

export function isEmailVerificationToken(value: string) {
  return TOKEN_PATTERN.test(value);
}
