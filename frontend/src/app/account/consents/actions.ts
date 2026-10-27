"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { requestApi, toDisplayMessage } from "@/lib/api/transport";
import { readSessionToken, requireSession } from "@/lib/auth/session";
import { safeReturnPath } from "@/lib/auth/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "@/lib/auth/consents";
import type { ActionState, SessionResponse } from "@/lib/auth/types";

export async function acceptAccountConsents(_previous: ActionState, data: FormData): Promise<ActionState> {
  const session = await requireSession();
  if (CONSENT_DOCUMENTS.some(({ document }) => data.get(document) !== "on")) return { message: "안내를 읽고 두 필수 동의에 체크하세요." };
  const result = await requestApi<SessionResponse>("/v1/sessions/current/consents", { method: "POST", token: await readSessionToken(), body: { consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })) } });
  if (!result.ok) return { message: toDisplayMessage(result.error) };
  revalidatePath("/", "layout");
  redirect(workspaceHref(safeReturnPath(data.get("returnTo"), session.user_account.role === "operator"), session.organization ? new URLSearchParams({ organization_id: session.organization.id }) : ""));
}
