"use client";

import { useEffect, useRef, useState } from "react";
import { formatKrw } from "@/lib/format";
import { canRetryInvoicePayment, invoicePaymentRetryUnavailableMessage } from "@/lib/invoice-payment-retry";
import type { PublicInvoice } from "@/lib/invoice-view-links";
import styles from "./InvoicePaymentRetry.module.css";

export type InvoicePaymentRequestState = "idle" | "submitting" | "unknown";

type Props = {
  invoice: PublicInvoice;
  getToken: () => string;
  isCurrentRequest: (token: string) => boolean;
  onStateChange: (state: InvoicePaymentRequestState, token: string) => void;
  onResult: (invoice: PublicInvoice, token: string) => void;
  onRefresh: () => void;
};

export function InvoicePaymentRetry({ invoice, getToken, isCurrentRequest, onStateChange, onResult, onRefresh }: Props) {
  const [isConfirming, setIsConfirming] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [hasUnconfirmedResult, setHasUnconfirmedResult] = useState(false);
  const [message, setMessage] = useState("");
  const controllerRef = useRef<AbortController | null>(null);
  const isSubmissionPendingRef = useRef(false);
  const confirmationRef = useRef<HTMLHeadingElement>(null);
  const retryTitleRef = useRef<HTMLHeadingElement>(null);
  const startButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);
  useEffect(() => { if (isConfirming) confirmationRef.current?.focus(); }, [isConfirming]);
  useEffect(() => { if (isSubmitting || hasUnconfirmedResult) retryTitleRef.current?.focus(); }, [isSubmitting, hasUnconfirmedResult]);

  async function submit() {
    if (!isConfirming || isSubmissionPendingRef.current || hasUnconfirmedResult || !canRetryInvoicePayment(invoice)) return;
    const token = getToken();
    if (!isCurrentRequest(token)) return;
    isSubmissionPendingRef.current = true;
    setIsSubmitting(true); setMessage("");
    onStateChange("submitting", token);
    const controller = new AbortController();
    controllerRef.current = controller;
    try {
      const response = await fetch("/invoice-links/payment-retries", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, retry_version: invoice.payment_retry.version }),
        credentials: "omit", cache: "no-store", redirect: "error", signal: controller.signal,
      });
      if (controller.signal.aborted || !isCurrentRequest(token)) return;
      if (!response.ok) {
        setHasUnconfirmedResult(true); setIsConfirming(false);
        setMessage(response.status === 404
          ? "이 링크는 사용할 수 없습니다. 담당자에게 새 링크를 요청해 주세요."
          : response.status === 409
            ? "결제 상태가 변경되었거나 지금은 다시 시도할 수 없습니다. 결제 상태를 새로 조회해 주세요."
            : "결제 요청 결과를 확인하지 못했습니다. 다시 결제하지 말고 결제 상태를 새로 조회해 주세요.");
        onStateChange("unknown", token);
        return;
      }
      const updated = await response.json() as PublicInvoice;
      if (controller.signal.aborted || !isCurrentRequest(token)) return;
      if (updated.invoice_number !== invoice.invoice_number || updated.total_amount !== invoice.total_amount || updated.payment.mode !== invoice.payment.mode) throw new Error("invoice_mismatch");
      onResult(updated, token);
    } catch {
      if (controller.signal.aborted || !isCurrentRequest(token)) return;
      setHasUnconfirmedResult(true); setIsConfirming(false);
      setMessage("결제 요청 결과를 확인하지 못했습니다. 다시 결제하지 말고 결제 상태를 새로 조회해 주세요.");
      onStateChange("unknown", token);
    } finally {
      if (!controller.signal.aborted && isCurrentRequest(token)) setIsSubmitting(false);
      isSubmissionPendingRef.current = false;
    }
  }

  if (invoice.payment.status !== "failed") return null;
  if (!canRetryInvoicePayment(invoice)) return <p className={styles.unavailable}>{invoicePaymentRetryUnavailableMessage(invoice.payment_retry?.reason)}</p>;

  return <section className={styles.retry} aria-labelledby="invoice-payment-retry-title">
    <h3 id="invoice-payment-retry-title" ref={retryTitleRef} tabIndex={-1}>{hasUnconfirmedResult ? "결제 상태 확인" : "실패한 결제 다시 시도"}</h3>
    {!isSubmitting && !hasUnconfirmedResult && <p>청구서를 보낸 도입사가 이 링크에서의 재시도를 허용했습니다. 현재 등록된 카드로 {formatKrw(invoice.total_amount)}을 다시 결제할 수 있습니다.</p>}
    {message && <p role="alert" className={styles.error}>{message}</p>}
    {isSubmitting ? <p>결제 요청을 처리하고 있습니다. 결과가 나올 때까지 다시 결제를 요청하지 마세요.</p>
      : hasUnconfirmedResult ? <button type="button" onClick={onRefresh}>결제 상태 새로 조회</button>
        : !isConfirming ? <button ref={startButtonRef} type="button" onClick={() => setIsConfirming(true)}>결제 다시 시도</button>
          : <div className={styles.confirmation}>
            <h4 ref={confirmationRef} tabIndex={-1}>등록 카드로 다시 결제할까요?</h4>
            <dl><div><dt>청구서</dt><dd>{invoice.invoice_number}</dd></div><div><dt>결제 모드</dt><dd>{invoice.payment.mode === "test" ? "테스트 결제" : "실결제"}</dd></div><div><dt>결제할 금액</dt><dd>{formatKrw(invoice.total_amount)}</dd></div></dl>
            <p>{invoice.payment.mode === "test" ? "테스트 모드로 결제를 다시 요청합니다." : "아래 버튼을 누르면 등록된 카드로 표시된 금액의 결제를 다시 요청합니다."} 이전 결제가 완료되거나 결과 확인 중으로 바뀌면 추가로 결제하지 않습니다.</p>
            <div className={styles.controls}><button className={styles.confirmButton} type="button" onClick={submit}>{formatKrw(invoice.total_amount)}{invoice.payment.mode === "test" ? " 테스트" : ""} 결제 다시 시도</button><button type="button" onClick={() => { setIsConfirming(false); window.requestAnimationFrame(() => startButtonRef.current?.focus()); }}>돌아가기</button></div>
          </div>}
  </section>;
}
