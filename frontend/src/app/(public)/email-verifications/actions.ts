"use server";

import { headers } from "next/headers";
import { requestApi, toDisplayMessage } from "@/lib/api/transport";
import { CAPTCHA_REQUEST_TIMEOUT_MS, captchaMessage, turnstileField } from "@/lib/auth/captcha";
import { isValidEmail } from "@/lib/auth/credentials";
import { isEmailVerificationToken } from "@/lib/auth/email-verification";
import type { ActionState } from "@/lib/auth/types";

const EXPIRED_LINK_MESSAGE = "링크가 만료됐거나 이미 사용됐어요.";
const RETRY_MESSAGE = "인증하지 못했어요. 잠시 뒤 이메일 인증 버튼을 다시 눌러 주세요.";

// 링크를 더 쓸 수 없을 때만 로그인과 다시 받기를 권한다. 전송 실패나 서버 오류는 다시 누르면 된다.
export type ConfirmEmailVerificationState = ActionState & { isLinkUnusable?: boolean };

export async function resendEmailVerificationAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  const email = data.get("email");
  if (typeof email !== "string" || !isValidEmail(email.trim())) return { message: "가입한 이메일 주소를 입력하세요." };
  const result = await requestApi<void>("/v1/email-verifications", {
    method: "POST",
    clientIp: (await headers()).get("x-meterengine-client-ip") ?? undefined,
    body: { email: email.trim(), ...turnstileField(data) },
    isEmptyResponse: true,
    timeoutMs: CAPTCHA_REQUEST_TIMEOUT_MS,
  });
  if (!result.ok) return { message: captchaMessage(result.error) ?? toDisplayMessage(result.error) };
  return { message: "가입한 이메일이라면 몇 분 안에 새 인증 메일이 도착해요.", done: true };
}

export async function confirmEmailVerificationAction(_prev: ConfirmEmailVerificationState, data: FormData): Promise<ConfirmEmailVerificationState> {
  const token = data.get("token");
  if (typeof token !== "string" || !isEmailVerificationToken(token)) return { message: EXPIRED_LINK_MESSAGE, isLinkUnusable: true };
  const result = await requestApi<void>("/v1/email-verification-confirmations", { method: "POST", body: { token } });
  if (!result.ok) return result.error.code === "email_verification_not_found" ? { message: EXPIRED_LINK_MESSAGE, isLinkUnusable: true } : { message: RETRY_MESSAGE };
  return { message: "이메일을 인증했어요.", done: true };
}
