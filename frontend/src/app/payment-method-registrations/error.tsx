"use client";

import { RegistrationFrame } from "@/components/payment-registration/RegistrationFrame";

export default function PaymentMethodRegistrationError() {
  return <RegistrationFrame title="등록 정보를 불러오지 못했습니다">
    <p>잠시 후 이 페이지를 새로 열어 주세요.</p>
  </RegistrationFrame>;
}
