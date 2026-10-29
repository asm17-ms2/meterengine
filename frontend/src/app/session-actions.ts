"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { headers } from "next/headers";
import { requestApi, toDisplayMessage } from "@/lib/api/transport";
import { clearSession, readSessionToken, requireSession } from "@/lib/auth/session";
import type { ActionState, SessionResponse } from "@/lib/auth/types";
import { workspaceHref } from "@/lib/workspace-href";
import { expiredLoginHref } from "@/lib/auth/navigation";

export async function logoutAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  void _prev;
  const returnTo = data.get("returnTo");
  const token = await readSessionToken();
  if (token) {
    const result = await requestApi<void>("/v1/sessions/current", { method: "DELETE", token });
    if (!result.ok && result.error.status !== 401) return { message: "로그아웃을 완료하지 못했습니다. 다시 시도하세요." };
  }
  await clearSession();
  revalidatePath("/", "layout");
  redirect(typeof returnTo === "string" && /^\/organization-invitations\/me_invite_[A-Za-z0-9]{32}$/.test(returnTo) ? returnTo : "/login");
}

export async function switchOrganizationAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  const session = await requireSession();
  const id = data.get("organization_id");
  if (typeof id !== "string" || !session.organizations.some(org => org.id === id)) return { message: "소속된 도입사를 선택하세요." };
  const result = await requestApi<SessionResponse>("/v1/sessions/current/organization", {
    method: "PUT", token: await readSessionToken(), body: { organization_id: id },
  });
  if (!result.ok) {
    if (result.error.status === 401) redirect(expiredLoginHref((await headers()).get("x-meterengine-current-path")));
    return { message: toDisplayMessage(result.error) };
  }
  revalidatePath("/", "layout");
  redirect(result.data.organization?.status === "active" ? workspaceHref("/overview", new URLSearchParams({ organization_id: result.data.organization.id })) : "/organizations");
}
