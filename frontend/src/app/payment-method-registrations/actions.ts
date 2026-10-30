"use server";

import { requestApi } from "@/lib/api/transport";
import { beginRegistrationFlow, isValidRegistrationToken } from "./flow";
import type { PaymentMethodRegistrationResponse } from "./types";

export async function prepareRegistration(token: string, hasConsent: boolean) {
  if (!isValidRegistrationToken(token) || hasConsent !== true) {
    return { ok: false as const, message: "자동결제 등록에 동의한 뒤 계속해 주세요." };
  }
  const registration = await requestApi<PaymentMethodRegistrationResponse>(`/v1/payment-method-registrations/${encodeURIComponent(token)}`);
  if (!registration.ok) {
    return { ok: false as const, message: registration.error.status === 404
      ? "카드 등록 링크가 만료되었거나 이미 사용되었습니다. 링크를 보낸 담당자에게 새 링크를 요청해 주세요."
      : "등록 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요." };
  }
  return { ok: true as const, nonce: await beginRegistrationFlow(token, registration.data.customer_key) };
}
