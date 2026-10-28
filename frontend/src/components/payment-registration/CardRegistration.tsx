"use client";

import Script from "next/script";
import { useState } from "react";
import { Alert, Button, Checkbox } from "antd";
import { prepareRegistration } from "@/app/payment-method-registrations/actions";
import type { PaymentMethodRegistrationResponse } from "@/app/payment-method-registrations/types";
import styles from "./registration.module.css";

type TossPaymentsSdk = (clientKey: string) => {
  payment: (options: { customerKey: string }) => {
    requestBillingAuth: (options: { method: "CARD"; successUrl: string; failUrl: string; customerName: string }) => Promise<void>;
  };
};

declare global {
  interface Window {
    // eslint-disable-next-line @typescript-eslint/naming-convention -- 토스 SDK의 전역 이름이다.
    TossPayments?: TossPaymentsSdk;
  }
}

export function CardRegistration({ token, registration }: { token: string; registration: PaymentMethodRegistrationResponse }) {
  const [hasConsent, setHasConsent] = useState(false);
  const [isSdkReady, setIsSdkReady] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const expiresAt = new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "Asia/Seoul" }).format(new Date(registration.expires_at));

  async function registerCard() {
    if (!hasConsent || isSubmitting || !window.TossPayments) return;
    setIsSubmitting(true);
    setErrorMessage("");
    try {
      const prepared = await prepareRegistration(token, hasConsent);
      if (!prepared.ok) {
        setErrorMessage(prepared.message);
        return;
      }
      const successUrl = new URL(`/payment-method-registrations/${encodeURIComponent(token)}/success`, window.location.origin);
      const failUrl = new URL(`/payment-method-registrations/${encodeURIComponent(token)}/fail`, window.location.origin);
      successUrl.searchParams.set("state", prepared.nonce);
      failUrl.searchParams.set("state", prepared.nonce);
      await window.TossPayments(registration.client_key).payment({ customerKey: registration.customer_key }).requestBillingAuth({
        method: "CARD", successUrl: successUrl.toString(), failUrl: failUrl.toString(), customerName: registration.customer_name,
      });
    } catch (error: unknown) {
      const isCanceled = typeof error === "object" && error !== null && "code" in error && error.code === "USER_CANCEL";
      setErrorMessage(isCanceled ? "카드 등록을 취소했습니다. 준비되면 다시 시작해 주세요." : "카드 인증창을 열지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return <div className={styles.form}>
    <Script id="toss-payments-sdk-v2" src="https://js.tosspayments.com/v2/standard" strategy="afterInteractive"
      onReady={() => setIsSdkReady(true)} onError={() => setErrorMessage("카드 인증 서비스를 불러오지 못했습니다. 페이지를 새로 열어 주세요.")} />
    {registration.mode === "test" ? <Alert type="info" showIcon title="테스트 카드 등록" description="실제 출금이 발생하지 않는 테스트 결제용 카드입니다." /> : <Alert type="warning" showIcon title="실제 결제용 카드 등록" description="등록한 카드는 서비스 이용료를 실제로 결제할 때 사용합니다." />}
    <dl className={styles.summary}>
      <div><dt>서비스 제공자</dt><dd>{registration.organization_name}</dd></div>
      <div><dt>고객명</dt><dd>{registration.customer_name}</dd></div>
      <div><dt>링크 유효기간</dt><dd>{expiresAt}까지</dd></div>
    </dl>
    <fieldset className={styles.consent}>
      <legend>자동결제 등록 동의</legend>
      <ul className={styles.terms}><li>{registration.organization_name}의 서비스 이용료를 결제할 카드를 등록합니다. 지금은 카드 등록만 진행하며 결제는 일어나지 않습니다.</li><li>이후 청구가 확정되면 사용량에 따라 정해진 이용료를 {registration.organization_name}가 이 카드로 결제합니다. 결제할 때마다 따로 승인하지 않으며, 금액은 청구마다 달라질 수 있습니다.</li><li>자동결제 중단, 카드 변경과 결제 조건은 {registration.organization_name} 담당자에게 요청해 주세요.</li></ul>
      <Checkbox checked={hasConsent} onChange={(event) => setHasConsent(event.target.checked)} disabled={isSubmitting}>
        [필수] 위 자동결제 안내를 확인하고 카드 등록에 동의합니다.
      </Checkbox>
    </fieldset>
    {errorMessage && <Alert type="error" showIcon title={errorMessage} />}
    <Button type="primary" size="large" block disabled={!hasConsent || !isSdkReady} loading={isSubmitting} onClick={registerCard}>
      토스에서 카드 등록하기
    </Button>
    <p className={styles.note}>{!isSdkReady && !errorMessage ? "카드 인증창을 준비하고 있습니다." : "다음 화면에서 카드 정보를 입력하고 본인인증을 진행합니다."}</p>
  </div>;
}
