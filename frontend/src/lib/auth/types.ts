import type { OrganizationRole } from "@/lib/auth/permissions";

export type OrganizationStatus = "pending" | "active" | "rejected" | "suspended";
// live_environment_opened_at이 null이면 실제 환경이 잠긴 조직이다.
export type SessionOrganization = { id: string; name: string; status: OrganizationStatus; live_environment_opened_at?: string | null };
// 필드가 없는 응답은 잠금을 모르는 서버의 것이라 열린 것으로 본다. 잠금은 서버가 다시 막는다.
export function isLiveEnvironmentOpen(organization: Pick<SessionOrganization, "live_environment_opened_at">): boolean {
  return organization.live_environment_opened_at !== null;
}
export type SessionUserAccount = { id: string; email: string; name: string; role: "member" | "operator" };
export type SessionResponse = {
  user_account: SessionUserAccount;
  organization_role?: OrganizationRole | null;
  scopes: string[];
  consent_required?: boolean;
  organization: SessionOrganization | null;
  organizations: SessionOrganization[];
  expires_at: string;
};
export type SessionTokenResponse = Omit<SessionResponse, "organizations"> & { token: string };
export type SignupResponse = { organization: SessionOrganization; user_account: SessionUserAccount & { email_verified?: boolean } };
export type OrganizationResponse = SessionOrganization & { created_at: string };
export type UserAccountResponse = SessionUserAccount & {
  organizations: SessionOrganization[];
  created_at: string;
};
export type ActionState = { message?: string; done?: boolean };

export const ORGANIZATION_STATUS_LABELS: Record<OrganizationStatus, string> = {
  pending: "가입 대기", active: "사용 중", rejected: "가입 거절", suspended: "이용 정지",
};
