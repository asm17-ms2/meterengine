import { requestApi } from "@/lib/api/transport";
import { CardRegistration } from "@/components/payment-registration/CardRegistration";
import { RegistrationFrame } from "@/components/payment-registration/RegistrationFrame";
import { isValidRegistrationToken } from "../flow";
import type { PaymentMethodRegistrationResponse } from "../types";

export default async function PaymentMethodRegistrationPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const registration = isValidRegistrationToken(token)
    ? await requestApi<PaymentMethodRegistrationResponse>(`/v1/payment-method-registrations/${encodeURIComponent(token)}`)
    : null;
  if (!registration?.ok) {
    const isUnavailable = registration !== null && registration.error.status !== 404;
    return <RegistrationFrame title={isUnavailable ? "등록 정보를 불러오지 못했습니다" : "사용할 수 없는 등록 링크입니다"}>
      <p>{isUnavailable
        ? "잠시 후 이 페이지를 새로 열어 주세요. 계속 연결되지 않으면 링크를 보낸 담당자에게 문의해 주세요."
        : "등록 링크가 만료되었거나 이미 사용되었습니다. 링크를 보낸 담당자에게 새 카드 등록 링크를 요청해 주세요."}</p>
    </RegistrationFrame>;
  }
  return <RegistrationFrame title="결제에 사용할 카드를 등록해 주세요" organizationName={registration.data.organization_name}>
    <CardRegistration token={token} registration={registration.data} />
  </RegistrationFrame>;
}
