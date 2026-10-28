export const AUDIT_ACTOR_TYPES: Record<string, string> = { user_account: "로그인 사용자", api_key: "API 키", system: "시스템", anonymous: "미인증 요청", invoice_view_link: "고객 조회 링크" };

export function auditActorLabel(actorType: string, actorId: string | null, names: ReadonlyMap<string, string>): string {
  return (actorId ? names.get(actorId) : undefined) ?? AUDIT_ACTOR_TYPES[actorType] ?? actorType;
}
