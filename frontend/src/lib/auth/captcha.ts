import type { ApiError } from "@/lib/api/transport";

// 가입과 다시 받기는 백엔드가 Siteverify를 최대 4초 기다린 뒤 비밀번호 해시와 저장까지 하므로 기본 5초보다 길게 기다린다.
export const CAPTCHA_REQUEST_TIMEOUT_MS = 15_000;

// 위젯을 켜지 않은 환경에서는 필드를 보내지 않는다. 서버가 캡차를 끄면 토큰 없이 통과한다.
export function turnstileField(data: FormData): { turnstile_token?: string } {
  const value = data.get("turnstile_token");
  return typeof value === "string" && value ? { turnstile_token: value } : {};
}

export function captchaMessage(error: ApiError): string | undefined {
  if (error.code === "captcha_failed") return "사람인지 확인하는 단계를 다시 마친 뒤 시도해 주세요.";
  if (error.code === "captcha_unavailable") return "확인 서비스가 응답하지 않아 요청을 받지 못했어요. 잠시 뒤 다시 시도해 주세요.";
  return undefined;
}
