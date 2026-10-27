import "server-only";

import { cache } from "react";
import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import { requestApi } from "@/lib/api/transport";
import type { SessionResponse } from "@/lib/auth/types";
import { workspaceHref } from "@/lib/workspace-href";
import { safeReturnPath } from "@/lib/auth/navigation";

export const SESSION_COOKIE = "me_session";

export async function readSessionToken(): Promise<string | undefined> {
  return (await cookies()).get(SESSION_COOKIE)?.value;
}

export const requireSession = cache(async (): Promise<SessionResponse> => {
  const token = await readSessionToken();
  if (!token) redirect("/login");
  const result = await requestApi<SessionResponse>("/v1/sessions/current", { token });
  if (!result.ok) {
    if (result.error.status === 401 || result.error.status === 403) redirect("/login?reason=expired");
    throw new Error("로그인 정보를 확인하지 못했습니다. 잠시 후 다시 시도하세요.");
  }
  return result.data;
});

export async function requireOperator(): Promise<SessionResponse> {
  const session = await requireSession();
  if (session.user_account.role !== "operator") redirect(workspaceHref("/customers", session.organization ? new URLSearchParams({ organization_id: session.organization.id }) : ""));
  if (session.consent_required) redirect("/account/consents?returnTo=%2Fadmin");
  return session;
}

export async function requireTenantSession(): Promise<SessionResponse> {
  const session = await requireSession();
  if (session.consent_required) {
    const path = safeReturnPath((await headers()).get("x-meterengine-current-path"), session.user_account.role === "operator");
    const returnTo = workspaceHref(path, session.organization ? new URLSearchParams({ organization_id: session.organization.id }) : "");
    redirect(`/account/consents?returnTo=${encodeURIComponent(returnTo)}`);
  }
  if (!session.organization || session.organization.status !== "active") redirect("/organizations");
  return session;
}

export async function saveSession(token: string, expiresAt: string) {
  const expires = new Date(expiresAt);
  if (!token || !Number.isFinite(expires.getTime()) || expires.getTime() <= Date.now()) {
    throw new Error("로그인 응답을 확인하지 못했습니다.");
  }
  (await cookies()).set(SESSION_COOKIE, token, {
    httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", expires,
  });
}

export async function clearSession() {
  (await cookies()).delete(SESSION_COOKIE);
}
