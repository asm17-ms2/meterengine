"use server";

import { assertLocalRequest } from "@/lib/local-request";
import { clientFor, toErrorView } from "@/lib/meter-engine";

export type RegistrationState = { status: "idle" } | { status: "ready"; url: string; expiresAt: string; mode: "test" | "live" } | { status: "failed"; message: string };

export async function createPaymentMethodRegistrationAction(_previous: RegistrationState, formData: FormData): Promise<RegistrationState> {
  await assertLocalRequest();
  const target = formData.get("target");
  const id = formData.get("customer_id");
  if (typeof target !== "string" || typeof id !== "string" || !/^[0-9a-f-]{36}$/i.test(id)) {
    return { status: "failed", message: "고객을 다시 선택한 뒤 카드 등록을 시작하세요." };
  }
  try {
    const client = clientFor(target, { maxRetries: 0 });
    await client.getCustomer(id);
    const registration = await client.createPaymentMethodRegistration(id);
    if (registration.token === undefined) throw new Error("카드 등록 링크를 받지 못했습니다.");
    const baseUrl = new URL(process.env.METERENGINE_CONSOLE_URL ?? "http://127.0.0.1:3000");
    if (!["http:", "https:"].includes(baseUrl.protocol) || baseUrl.username || baseUrl.password) throw new Error("카드 등록 화면 주소 설정을 확인하세요.");
    const url = new URL(`/payment-method-registrations/${encodeURIComponent(registration.token)}`, baseUrl);
    return { status: "ready", url: url.href, expiresAt: registration.expires_at, mode: registration.mode };
  } catch (error) {
    const detail = toErrorView(error);
    return { status: "failed", message: detail.status === 403 ? "카드 등록 링크를 만들 권한이 없습니다. 도입사 서버의 API 키에 admin 권한을 설정하세요." : detail.message };
  }
}
