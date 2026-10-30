"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { requestApi, toDisplayMessage } from "@/lib/api/transport";
import { readSessionToken, requireSession } from "@/lib/auth/session";
import { returnOrganizationId, safeReturnPath, withoutOrganizationId } from "@/lib/auth/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "@/lib/auth/consents";
import type { ActionState, SessionResponse } from "@/lib/auth/types";

export async function acceptAccountConsents(_previous: ActionState, data: FormData): Promise<ActionState> {
  const session = await requireSession();
  const newPassword = String(data.get("newPassword") ?? "");
  if (newPassword.length < 8 || new TextEncoder().encode(newPassword).length > 72) return { message: "새 비밀번호는 8자 이상 72자 이하로 입력하세요. 한글은 1자를 3자로 계산합니다." };
  if (newPassword !== String(data.get("newPasswordConfirm") ?? "")) return { message: "새 비밀번호와 확인 칸이 다릅니다." };
  if (CONSENT_DOCUMENTS.some(({ document }) => data.get(document) !== "on")) return { message: "안내를 읽고 두 필수 동의에 체크하세요." };
  const result = await requestApi<SessionResponse>("/v1/sessions/current/consents", { method: "POST", token: await readSessionToken(), body: { consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })), new_password: newPassword } });
  if (!result.ok) return { message: toDisplayMessage(result.error) };
  revalidatePath("/", "layout");
  const returnPath = safeReturnPath(data.get("returnTo"), session.user_account.role === "operator");
  const requested = returnOrganizationId(returnPath);
  redirect(workspaceHref(requested && requested !== session.organization?.id ? withoutOrganizationId(returnPath) : returnPath, session.organization ? new URLSearchParams({ organization_id: session.organization.id }) : ""));
}
