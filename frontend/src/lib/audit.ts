export const AUDIT_ACTOR_TYPES: Record<string, string> = { user_account: "로그인 사용자", api_key: "API 키", system: "시스템", anonymous: "미인증 요청", invoice_view_link: "고객 조회 링크" };

export function auditActorLabel(actorType: string, actorId: string | null, names: ReadonlyMap<string, string>): string {
  return (actorId ? names.get(actorId) : undefined) ?? AUDIT_ACTOR_TYPES[actorType] ?? actorType;
}

export const AUDIT_ACTIONS: Record<string, string> = {
  api_request_completed: "API 요청", session_created: "로그인", session_deleted: "로그아웃", signup_created: "도입사 가입 신청",
  organization_status_updated: "도입사 상태 변경", user_account_created: "운영자 계정 생성", user_account_role_updated: "플랫폼 권한 변경", organization_member_created: "사용자 소속 추가",
  organization_member_archived: "사용자 소속 보관", organization_member_role_updated: "도입사 역할 변경", organization_invitation_created: "관리자 초대",
  organization_invitation_accepted: "초대 수락", organization_invitation_revoked: "초대 취소", api_key_created: "API 키 발급", api_key_deleted: "API 키 폐기",
  invoice_finalized: "인보이스 확정", price_rate_created: "단가 버전 등록", price_rate_deleted: "단가 적용 종료", price_plan_created: "요금제 등록",
  invoice_view_link_created: "고객 조회 링크 발급", invoice_view_link_revoked: "고객 조회 링크 폐기", invoice_viewed: "고객 청구서 조회",
  payment_retry_requested: "고객 결제 재시도 요청",
  customer_created: "고객 등록", customer_updated: "고객 정보 변경", customer_archived: "고객 보관", customer_restored: "고객 복원",
  billable_metric_created: "미터 등록", billable_metric_updated: "미터 수정", billable_metric_archived: "미터 보관", billable_metric_restored: "미터 복원", price_rate_archived: "단가 보관", price_rate_restored: "단가 복원",
  price_plan_archived: "요금제 보관", price_plan_restored: "요금제 복원", payment_attempt_created: "결제 요청", payment_cancellation_created: "결제 취소",
  organization_member_scopes_updated: "사용자 업무 권한 변경", api_key_scopes_updated: "API 키 권한 변경", session_organization_updated: "도입사 전환", user_account_consents_accepted: "이용 안내 동의",
  user_account_password_updated: "비밀번호 변경", payment_setting_updated: "결제 모드 변경", toss_payments_credential_updated: "토스 키 저장", toss_payments_credential_archived: "토스 키 보관",
  supplier_updated: "공급자 정보 변경", payment_method_created: "카드 등록", payment_method_archived: "등록 카드 보관", webhook_endpoint_created: "수신 주소 등록", webhook_endpoint_updated: "수신 주소 수정",
  webhook_endpoint_archived: "수신 주소 보관", webhook_endpoint_restored: "수신 주소 복원", webhook_delivery_retried: "통보 다시 보내기",
};

// 지금은 남기지 않는 행동은 옛 기록을 읽을 이름만 두고 행동 조건 선택지에서는 뺀다.
const RETIRED_AUDIT_ACTIONS = new Set(["price_rate_deleted"]);
export const AUDIT_ACTION_OPTIONS = Object.entries(AUDIT_ACTIONS).filter(([action]) => !RETIRED_AUDIT_ACTIONS.has(action));
