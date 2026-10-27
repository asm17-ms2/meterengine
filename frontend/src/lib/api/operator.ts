import "server-only";

import { redirect } from "next/navigation";
import { requestApi } from "@/lib/api/transport";
import { readSessionToken, requireOperator } from "@/lib/auth/session";
import type { OrganizationResponse, UserAccountResponse } from "@/lib/auth/types";

export async function operatorRequest<T>(path: string, options: { method?: "GET" | "POST" | "PUT"; body?: unknown } = {}) {
  await requireOperator();
  const result = await requestApi<T>(path, { ...options, token: await readSessionToken() });
  if (!result.ok && result.error.status === 401) redirect("/login?reason=expired");
  return result;
}

export function listOrganizations() {
  return operatorRequest<{ organizations: OrganizationResponse[] }>("/v1/organizations");
}

export function listUserAccounts() {
  return operatorRequest<{ user_accounts: UserAccountResponse[] }>("/v1/user-accounts");
}
