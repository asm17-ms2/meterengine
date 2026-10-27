"use server";

import { redirect } from "next/navigation";
import { requestApi, toDisplayMessage } from "@/lib/api/transport";
import { safeReturnPath } from "@/lib/auth/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { saveSession } from "@/lib/auth/session";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "@/lib/auth/consents";
import type { ActionState, SessionTokenResponse, SignupResponse } from "@/lib/auth/types";

function textField(data: FormData, name: string) {
  const value = data.get(name);
  return typeof value === "string" ? value : "";
}

export async function loginAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  const email = textField(data, "email").trim();
  const password = textField(data, "password");
  if (!email || !password) return { message: "이메일과 비밀번호를 입력하세요." };
  const result = await requestApi<SessionTokenResponse>("/v1/sessions", { method: "POST", body: { email, password } });
  if (!result.ok) {
    if (result.error.code === "organization_not_active") return { message: "사용 가능한 도입사가 없습니다. 가입 승인 전이거나 이용이 정지된 상태일 수 있습니다. 운영자에게 확인한 뒤 다시 로그인하세요." };
    if (result.error.status === 401) return { message: "이메일 또는 비밀번호가 올바르지 않습니다." };
    return { message: toDisplayMessage(result.error) };
  }
  await saveSession(result.data.token, result.data.expires_at);
  const destination = workspaceHref(safeReturnPath(data.get("returnTo"), result.data.user_account.role === "operator"), result.data.organization ? new URLSearchParams({ organization_id: result.data.organization.id }) : "");
  if (result.data.consent_required) redirect(`/account/consents?returnTo=${encodeURIComponent(destination)}`);
  redirect(destination);
}

export async function signupAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  const organizationName = textField(data, "organization_name").trim();
  const name = textField(data, "name").trim();
  const email = textField(data, "email").trim();
  const password = textField(data, "password");
  if (!organizationName || organizationName.length > 255 || !name || name.length > 255) return { message: "도입사 이름과 담당자 이름을 255자 이내로 입력하세요." };
  if (email.length > 254 || !/^\S+@\S+\.\S+$/.test(email)) return { message: "올바른 이메일 주소를 입력하세요." };
  if (password.length < 8 || password.length > 72 || new TextEncoder().encode(password).length > 72) return { message: "비밀번호는 8자 이상, UTF-8 기준 72바이트 이하로 입력하세요." };
  if (CONSENT_DOCUMENTS.some(({ document }) => data.get(document) !== "on")) return { message: "두 안내를 읽고 필수 동의에 체크하세요." };
  const result = await requestApi<SignupResponse>("/v1/signups", {
    method: "POST", body: { organization_name: organizationName, name, email, password,
      consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
    },
  });
  if (!result.ok) return { message: toDisplayMessage(result.error) };
  redirect("/pending");
}
