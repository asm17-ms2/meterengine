"use server";

import { redirect } from "next/navigation";
import { headers } from "next/headers";
import { requestApi, toDisplayMessage } from "@/lib/api/transport";
import { returnOrganizationId, safeReturnPath, withoutOrganizationId } from "@/lib/auth/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { config } from "@/lib/config";
import { withoutClosedLegacyEnvironment } from "@/lib/environment";
import { saveSession } from "@/lib/auth/session";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "@/lib/auth/consents";
import { isValidEmail, isValidPassword, NAME_MAX_LENGTH, PASSWORD_RULE_MESSAGE } from "@/lib/auth/credentials";
import type { ActionState, SessionResponse, SessionTokenResponse, SignupResponse } from "@/lib/auth/types";
import { CAPTCHA_REQUEST_TIMEOUT_MS, captchaMessage, turnstileField } from "@/lib/auth/captcha";

function textField(data: FormData, name: string) {
  const value = data.get(name);
  return typeof value === "string" ? value : "";
}

export async function loginAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  const email = textField(data, "email").trim();
  const password = textField(data, "password");
  if (!email || !password) return { message: "이메일과 비밀번호를 입력하세요." };
  const clientIp = (await headers()).get("x-meterengine-client-ip") ?? undefined;
  const result = await requestApi<SessionTokenResponse>("/v1/sessions", { method: "POST", body: { email, password }, clientIp });
  if (!result.ok) {
    if (result.error.code === "email_not_verified") redirect("/email-verifications?reason=unverified");
    if (result.error.code === "organization_not_active") return { message: "지금은 콘솔을 이용할 수 없어요. 가입 승인 전이거나, 승인되지 않았거나, 이용이 정지됐거나, 조직에서 접근이 중단됐을 수 있어요." };
    if (result.error.status === 401) return { message: "이메일 또는 비밀번호가 맞지 않아요." };
    return { message: toDisplayMessage(result.error) };
  }
  await saveSession(result.data.token, result.data.expires_at);
  const returnPath = withoutClosedLegacyEnvironment(safeReturnPath(data.get("returnTo"), result.data.user_account.role === "operator"), config.showLegacyEnvironment);
  const requested = returnOrganizationId(returnPath);
  let organization = result.data.organization;
  if (requested && organization && requested !== organization.id && !result.data.consent_required) {
    const switched = await requestApi<SessionResponse>("/v1/sessions/current/organization", { method: "PUT", token: result.data.token, body: { organization_id: requested } });
    if (switched.ok && switched.data.organization?.status === "active") organization = switched.data.organization;
  }
  const destination = workspaceHref(requested && requested !== organization?.id ? withoutOrganizationId(returnPath) : returnPath, organization ? new URLSearchParams({ organization_id: organization.id }) : "");
  if (result.data.consent_required) redirect(`/account/consents?returnTo=${encodeURIComponent(destination)}`);
  redirect(destination);
}

export async function signupAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  const organizationName = textField(data, "organization_name").trim();
  const name = textField(data, "name").trim();
  const email = textField(data, "email").trim();
  const password = textField(data, "password");
  if (!organizationName) return { message: "조직 이름을 입력하세요." };
  if (!name) return { message: "담당자 이름을 입력하세요." };
  if (organizationName.length > NAME_MAX_LENGTH || name.length > NAME_MAX_LENGTH) return { message: "조직 이름과 담당자 이름은 255자 이내로 입력하세요." };
  if (!isValidEmail(email)) return { message: "올바른 이메일 주소를 입력하세요." };
  if (!isValidPassword(password)) return { message: PASSWORD_RULE_MESSAGE };
  if (CONSENT_DOCUMENTS.some(({ document }) => data.get(document) !== "on")) return { message: "이용약관과 개인정보 처리방침을 읽고 두 항목에 모두 체크하세요." };
  const result = await requestApi<SignupResponse>("/v1/signups", {
    clientIp: (await headers()).get("x-meterengine-client-ip") ?? undefined,
    method: "POST", timeoutMs: CAPTCHA_REQUEST_TIMEOUT_MS, body: { organization_name: organizationName, name, email, password,
      consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
      ...turnstileField(data),
    },
  });
  if (!result.ok) return { message: captchaMessage(result.error) ?? toDisplayMessage(result.error) };
  redirect(result.data.user_account.email_verified === false ? "/email-verifications" : "/pending");
}
