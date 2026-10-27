export const CONSENT_VERSION = "2026-10-26-preview";
export const CONSENT_DOCUMENTS = [
  {
    document: "terms_of_service",
    title: "이용 안내",
    text: "가입 신청으로 도입사와 사용자 계정을 만듭니다. 운영자가 도입사를 승인한 뒤 로그인할 수 있습니다. 로그인한 사용자는 소속된 도입사의 사용량, 고객과 청구 정보를 다룹니다.",
  },
  {
    document: "privacy_policy",
    title: "개인정보 처리 안내",
    text: "가입 신청에는 도입사 이름, 담당자 이름, 이메일과 비밀번호가 필요합니다. 계정 인증과 도입사 가입 승인에 사용하며, 비밀번호는 해시로 저장합니다. 동의한 문서의 종류와 판을 함께 기록합니다.",
  },
] as const;
