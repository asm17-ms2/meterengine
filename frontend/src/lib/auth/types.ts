export type OrganizationStatus = "pending" | "active" | "rejected" | "suspended";
export type SessionOrganization = { id: string; name: string; status: OrganizationStatus };
export type SessionUserAccount = { id: string; email: string; name: string; role: "member" | "operator" };
export type SessionResponse = {
  user_account: SessionUserAccount;
  organization_role?: "owner" | "admin" | "member" | null;
  scopes: string[];
  consent_required?: boolean;
  organization: SessionOrganization | null;
  organizations: SessionOrganization[];
  expires_at: string;
};
export type SessionTokenResponse = Omit<SessionResponse, "organizations"> & { token: string };
export type SignupResponse = { organization: SessionOrganization; user_account: SessionUserAccount };
export type OrganizationResponse = SessionOrganization & { created_at: string };
export type UserAccountResponse = SessionUserAccount & {
  organizations: SessionOrganization[];
  created_at: string;
};
export type ActionState = { message?: string; done?: boolean };

export const ORGANIZATION_STATUS_LABELS: Record<OrganizationStatus, string> = {
  pending: "승인 대기", active: "사용 중", rejected: "가입 거절", suspended: "이용 정지",
};
