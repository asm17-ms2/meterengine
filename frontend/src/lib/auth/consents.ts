// 문서의 판이자 시행일이다. /terms, /privacy 본문을 바꾸면 이 날짜도 함께 바꾼다.
export const CONSENT_VERSION = "2026-11-11";
export const CONSENT_DOCUMENTS = [
  { document: "terms_of_service", title: "이용약관", href: "/terms", checkLabel: "이용약관에 동의합니다 (필수)" },
  { document: "privacy_policy", title: "개인정보 처리방침", href: "/privacy", checkLabel: "개인정보 처리방침을 확인했습니다 (필수)" },
] as const;

export function consentEffectiveDate() {
  const [year, month, day] = CONSENT_VERSION.split("-").map(Number);
  return `${year}년 ${month}월 ${day}일`;
}
