import "server-only";

import { redirect } from "next/navigation";
import { headers } from "next/headers";
import { requestApi } from "@/lib/api/transport";
import { readSessionToken, requireOperator } from "@/lib/auth/session";
import type { OrganizationResponse, OrganizationStatus, UserAccountResponse } from "@/lib/auth/types";
import { expiredLoginHref } from "@/lib/auth/navigation";

export async function operatorRequest<T>(path: string, options: { method?: "GET" | "POST" | "PUT"; body?: unknown } = {}) {
  await requireOperator();
  const result = await requestApi<T>(path, { ...options, token: await readSessionToken() });
  if (!result.ok && result.error.status === 401) redirect(expiredLoginHref((await headers()).get("x-meterengine-current-path")));
  return result;
}

export function listOrganizations(status?: OrganizationStatus) {
  return operatorRequest<{ organizations: OrganizationResponse[] }>(status ? `/v1/organizations?status=${status}` : "/v1/organizations");
}

export type LiveEnvironmentApplicationStatus = "pending" | "approved" | "rejected";
export type LiveEnvironmentApplicationResponse = {
  id: string; organization_id: string; organization_name: string; business_registration_number: string; toss_payments_merchant_id: string;
  status: LiveEnvironmentApplicationStatus; business_status: "operating" | "suspended" | "unchecked"; tax_type: string | null; rejection_reason: string | null;
  created_at: string; reviewed_at: string | null; observed_toss_payments_merchant_id: string | null; merchant_id_checked_at: string | null;
};

export function listLiveEnvironmentApplications(status?: LiveEnvironmentApplicationStatus) {
  return operatorRequest<{ live_environment_applications: LiveEnvironmentApplicationResponse[] }>(status ? `/v1/live-environment-applications?status=${status}` : "/v1/live-environment-applications");
}

export function listUserAccounts() {
  return operatorRequest<{ user_accounts: UserAccountResponse[] }>("/v1/user-accounts");
}
