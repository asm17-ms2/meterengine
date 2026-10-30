import "server-only";

import { workspaceFetch } from "@/lib/api/workspace";
import type { OrganizationRole } from "@/lib/auth/permissions";
import type { ArchiveStatus } from "@/lib/list-query";

export type ManagedMemberResponse = {
  user_account_id: string;
  email: string;
  name: string;
  organization_role: OrganizationRole;
  scopes: string[];
  status: "active" | "archived";
  created_at: string;
  revoked_at: string | null;
};

export type OrganizationInvitationResponse = {
  scopes: string[];
  id: string;
  email: string;
  status: string;
  created_at: string;
  expires_at: string;
  accepted_at: string | null;
  revoked_at: string | null;
};

export type AuditLog = {
  id: string;
  occurred_at: string;
  organization_id: string | null;
  actor_type: "user_account" | "api_key" | "system" | "anonymous" | "invoice_view_link";
  actor_id: string | null;
  action: string;
  target_type: string;
  target_id: string;
  detail: Record<string, unknown>;
};

export type AuditLogsResponse = { audit_logs: AuditLog[]; total: number; page: number; size: number };

export function listOrganizationMembers(status: ArchiveStatus = "all") {
  return workspaceFetch<{ members: ManagedMemberResponse[] }>("/v1/organization/members", { status });
}

export function listOrganizationInvitations() {
  return workspaceFetch<{ invitations: OrganizationInvitationResponse[] }>("/v1/organization/invitations");
}
