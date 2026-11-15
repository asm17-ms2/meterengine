import "server-only";

import { cache } from "react";
import type { Metadata } from "next";
import { requestApi } from "@/lib/api/transport";
import { isValidRegistrationToken } from "./flow";
import type { PaymentMethodRegistrationResponse } from "./types";

// 탭 제목과 화면이 같은 요청 안에서 한 번만 조회하게 묶는다.
export const loadRegistration = cache(async (token: string) => isValidRegistrationToken(token)
  ? await requestApi<PaymentMethodRegistrationResponse>(`/v1/payment-method-registrations/${encodeURIComponent(token)}`)
  : null);

export function registrationMetadata(organizationName?: string): Metadata {
  return { title: { absolute: organizationName ? `${organizationName} 결제 카드 등록` : "결제 카드 등록" } };
}
