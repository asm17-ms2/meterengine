"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { cookies, headers } from "next/headers";
import { requestApi, toDisplayMessage } from "@/lib/api/transport";
import { readSessionToken, saveSession } from "@/lib/auth/session";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "@/lib/auth/consents";
import { isValidPassword, NAME_MAX_LENGTH, PASSWORD_RULE_MESSAGE } from "@/lib/auth/credentials";
import type { SessionTokenResponse } from "@/lib/auth/types";
import { workspaceHref } from "@/lib/workspace-href";

export type InvitationState = { message?: string; isAccepted?: boolean };
type Preview = { email: string; account_exists: boolean };

export async function signInForInvitation(data: FormData) {
  const token = String(data.get("token") ?? "");
  if (!/^me_invite_[A-Za-z0-9]{32}$/.test(token)) redirect("/login");
  (await cookies()).set("me_invitation_flow", token, { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/organization-invitations", maxAge: 1800 });
  redirect("/login?returnTo=%2Forganization-invitations%2Fcontinue");
}

export async function reviewConsentsForInvitation(data: FormData) {
  const token = String(data.get("token") ?? "");
  if (!/^me_invite_[A-Za-z0-9]{32}$/.test(token)) redirect("/login");
  (await cookies()).set("me_invitation_flow", token, { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/organization-invitations", maxAge: 1800 });
  redirect("/account/consents?returnTo=%2Forganization-invitations%2Fcontinue");
}

export async function acceptInvitationAction(_previous: InvitationState, data: FormData): Promise<InvitationState> {
  const token = String(data.get("token") ?? "");
  if (!/^me_invite_[A-Za-z0-9]{32}$/.test(token)) return { message: "초대 링크가 올바르지 않아요. 받은 링크를 다시 확인하세요." };
  const path = `/v1/organization-invitations/${encodeURIComponent(token)}`;
  const preview = await requestApi<Preview>(path);
  if (!preview.ok) return { message: toDisplayMessage(preview.error) };
  const sessionToken = await readSessionToken();
  let password = "";
  let body: unknown;
  if (preview.data.account_exists) {
    if (!sessionToken) return { message: "초대받은 이메일 계정으로 로그인한 뒤 이 링크를 다시 여세요." };
  } else {
    const name = String(data.get("name") ?? "").trim();
    password = String(data.get("password") ?? "");
    if (!name) return { message: "이름을 입력하세요." };
    if (name.length > NAME_MAX_LENGTH) return { message: "이름은 255자 이내로 입력하세요." };
    if (!isValidPassword(password)) return { message: PASSWORD_RULE_MESSAGE };
    if (CONSENT_DOCUMENTS.some(({ document }) => data.get(document) !== "on")) return { message: "이용약관과 개인정보 처리방침을 읽고 두 항목에 모두 체크하세요." };
    body = { name, password, consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })) };
  }
  const accepted = await requestApi<{ user_account_id: string; organization_id: string }>(`${path}/accept`, { method: "POST", token: preview.data.account_exists ? sessionToken : undefined, body });
  if (!accepted.ok) return { message: toDisplayMessage(accepted.error) };
  (await cookies()).set("me_invitation_flow", "", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/organization-invitations", maxAge: 0 });
  let activeToken = sessionToken;
  if (!preview.data.account_exists) {
    const login = await requestApi<SessionTokenResponse>("/v1/sessions", { method: "POST", body: { email: preview.data.email, password }, clientIp: (await headers()).get("x-meterengine-client-ip") ?? undefined });
    if (!login.ok) return { isAccepted: true, message: "초대를 수락했어요. 로그인 화면에서 새 계정으로 로그인해 주세요." };
    await saveSession(login.data.token, login.data.expires_at);
    activeToken = login.data.token;
  }
  const selected = await requestApi("/v1/sessions/current/organization", { method: "PUT", token: activeToken, body: { organization_id: accepted.data.organization_id } });
  if (!selected.ok) return { isAccepted: true, message: "초대를 수락했어요. 조직 선택 화면에서 참여한 조직을 골라 주세요." };
  revalidatePath("/", "layout");
  redirect(workspaceHref("/overview", new URLSearchParams({ organization_id: accepted.data.organization_id })));
}
