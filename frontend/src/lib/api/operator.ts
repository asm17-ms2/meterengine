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

export function listUserAccounts() {
  return operatorRequest<{ user_accounts: UserAccountResponse[] }>("/v1/user-accounts");
}
