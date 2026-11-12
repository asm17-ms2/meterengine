import "server-only";

export const config = {
  apiBaseUrl: process.env.METERENGINE_API_BASE_URL ?? "http://localhost:8080",
  // 비면 가입과 인증 메일 다시 받기에 Turnstile 위젯을 띄우지 않는다. 비밀 키는 백엔드에만 둔다.
  turnstileSiteKey: process.env.TURNSTILE_SITE_KEY ?? "",
} as const;
