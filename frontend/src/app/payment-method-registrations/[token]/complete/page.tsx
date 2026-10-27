import Link from "next/link";
import { requestApi } from "@/lib/api/transport";
import { RegistrationFrame } from "@/components/payment-registration/RegistrationFrame";
import styles from "@/components/payment-registration/registration.module.css";
import { isValidRegistrationToken, readRegistrationOutcome } from "../../flow";
import type { PaymentMethodRegistrationResponse } from "../../types";

export default async function RegistrationCompletePage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const outcome = isValidRegistrationToken(token) ? await readRegistrationOutcome(token) : null;
  const isSuccess = outcome?.status === "success";
  const registration = !isSuccess && isValidRegistrationToken(token)
    ? await requestApi<PaymentMethodRegistrationResponse>(`/v1/payment-method-registrations/${encodeURIComponent(token)}`)
    : null;
  const title = isSuccess ? "카드를 등록했습니다" : outcome?.status === "canceled" ? "카드 등록을 취소했습니다" : "카드 등록을 완료하지 못했습니다";
  return <RegistrationFrame title={title} organizationName={outcome?.organizationName}>
    <div className={styles.outcome} role="status">
      {isSuccess ? <>
        <p><strong>{outcome.customerName}</strong>의 결제 카드가 등록되었습니다.</p>
        <dl className={styles.summary}>
          <div><dt>등록 카드</dt><dd>{outcome.cardNumber}</dd></div>
          <div><dt>결제 구분</dt><dd>{outcome.mode === "test" ? "테스트 등록" : "실제 결제"}</dd></div>
        </dl>
        <p>지금은 카드 등록만 완료되었으며 결제 금액은 청구 시 별도로 안내됩니다. 이 창을 닫아도 됩니다.</p>
      </> : <>
        <p>{outcome?.status === "canceled"
          ? "카드가 등록되지 않았습니다. 등록을 계속하려면 아래 버튼을 눌러 주세요."
          : outcome?.status === "invalid"
            ? "등록 요청을 확인하지 못했습니다. 이 브라우저에서 등록을 처음부터 다시 시작해 주세요."
            : outcome?.status === "expired"
              ? "링크가 만료되었거나 이미 사용되었습니다. 링크를 보낸 담당자에게 등록 여부 확인과 새 링크를 요청해 주세요."
              : outcome?.status === "rejected"
                ? "카드 인증 또는 등록이 거절되었습니다. 카드 정보를 확인하고 다시 시도해 주세요."
                : "등록 결과를 확인하지 못했습니다. 링크를 보낸 담당자에게 카드 등록 여부를 먼저 확인해 주세요."}</p>
        {registration?.ok ? <Link className={styles.retry} href={`/payment-method-registrations/${encodeURIComponent(token)}`}>카드 등록 다시 시작</Link>
          : <p className={styles.note}>새 카드 등록 링크는 링크를 보낸 담당자에게 요청할 수 있습니다.</p>}
      </>}
    </div>
  </RegistrationFrame>;
}
