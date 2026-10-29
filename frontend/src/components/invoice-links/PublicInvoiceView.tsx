"use client";

import { useEffect, useRef, useState } from "react";
import { formatDecimal, formatDimensionValues, formatKoreanMonth, formatKrw, formatKstDateTime, formatQuantityBasis } from "@/lib/format";
import { isValidInvoiceViewToken, type PublicInvoice } from "@/lib/invoice-view-links";
import { canRetryInvoicePayment } from "@/lib/invoice-payment-retry";
import styles from "./PublicInvoiceView.module.css";
import { InvoicePaymentRetry, type InvoicePaymentRequestState } from "./InvoicePaymentRetry";

const STORAGE_KEY = "meterengine.invoice-view-token";
const PAYMENT_STATES: Record<string, { label: string; description: string }> = {
  unpaid: { label: "결제 전", description: "아직 결제가 완료되지 않았습니다. 납부 방법은 청구서를 보낸 담당자에게 문의해 주세요." },
  failed: { label: "결제 실패", description: "결제가 완료되지 않았습니다. 청구서를 보낸 담당자에게 문의해 주세요." },
  pending: { label: "결제 확인 중", description: "결제 결과를 확인하고 있습니다. 잠시 후 다시 조회해 주세요." },
  paid: { label: "납부 완료", description: "청구 금액의 납부가 완료되었습니다." },
  partially_canceled: { label: "부분 취소", description: "결제 일부가 취소되었습니다. 확정 청구액과 취소액을 별도로 표시합니다." },
  canceled: { label: "전액 취소", description: "결제가 전액 취소되었습니다. 확정 청구서는 보존되며 취소액을 다시 결제 대기액에 더하지 않습니다." },
  not_required: { label: "결제 불필요", description: "청구 금액이 0원이므로 결제할 금액이 없습니다." },
};
const UNREADY_MESSAGES = {
  missing: { title: "청구서 조회 정보가 없습니다", description: "이 주소만으로는 청구서를 열 수 없습니다. 받은 메일이나 메시지의 원래 조회 링크를 다시 열어 주세요. 링크를 찾을 수 없으면 청구서를 보낸 담당자에게 문의해 주세요." },
  invalid: { title: "사용할 수 없는 조회 링크입니다", description: "링크가 만료되었거나 폐기되었을 수 있습니다. 청구서를 보낸 담당자에게 새 링크를 요청해 주세요." },
  unavailable: { title: "청구서를 불러오지 못했습니다", description: "잠시 후 다시 조회해 주세요. 계속 연결되지 않으면 청구서를 보낸 담당자에게 문의해 주세요." },
};
const LINE_NAMES: Record<string, string> = { base_fee: "기본료", minimum_adjustment: "최소 과금 조정" };

type ViewState = { kind: "loading" } | { kind: "missing" } | { kind: "invalid" } | { kind: "unavailable" } | { kind: "ready"; invoice: PublicInvoice; revision: number };

export function PublicInvoiceView() {
  const tokenRef = useRef("");
  const requestRef = useRef(0);
  const paymentTitleRef = useRef<HTMLHeadingElement>(null);
  const paymentFocusRevisionRef = useRef<number | null>(null);
  const [view, setView] = useState<ViewState>({ kind: "loading" });
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [paymentRequestState, setPaymentRequestState] = useState<InvoicePaymentRequestState>("idle");

  async function load(token: string, isRefresh = false, signal?: AbortSignal) {
    const requestId = ++requestRef.current;
    if (!token) { setView({ kind: "missing" }); return; }
    if (!isValidInvoiceViewToken(token)) { setView({ kind: "invalid" }); return; }
    if (isRefresh) setIsRefreshing(true);
    try {
      const response = await fetch("/invoice-links/lookup", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token }), credentials: "omit", cache: "no-store", redirect: "error", signal,
      });
      if (requestId !== requestRef.current) return;
      if (!response.ok) {
        if (response.status === 404) { try { sessionStorage.removeItem(STORAGE_KEY); } catch {} tokenRef.current = ""; }
        setView({ kind: response.status === 404 ? "invalid" : "unavailable" });
        return;
      }
      const invoice = await response.json() as PublicInvoice;
      if (requestId !== requestRef.current) return;
      setView({ kind: "ready", invoice, revision: requestId });
      setPaymentRequestState("idle");
      if (isRefresh) setFeedback("최신 결제 상태를 조회했습니다.");
    } catch { if (!signal?.aborted && requestId === requestRef.current) setView({ kind: "unavailable" }); }
    finally { if (requestId === requestRef.current) setIsRefreshing(false); }
  }

  useEffect(() => {
    let controller: AbortController | null = null;
    function openLink(event?: HashChangeEvent) {
      const hash = window.location.hash.slice(1);
      if (hash === "main-content" && event) return;
      const fragment = hash === "main-content" ? "" : hash;
      requestRef.current += 1;
      if (fragment) window.history.replaceState(null, "", window.location.pathname + window.location.search);
      let token = fragment;
      try {
        if (fragment) {
          if (isValidInvoiceViewToken(fragment)) sessionStorage.setItem(STORAGE_KEY, fragment);
          else sessionStorage.removeItem(STORAGE_KEY);
        } else token = sessionStorage.getItem(STORAGE_KEY) ?? "";
      } catch {}
      controller?.abort();
      controller = new AbortController();
      const currentController = controller;
      tokenRef.current = token;
      void Promise.resolve().then(() => {
        if (currentController.signal.aborted) return;
        setView({ kind: "loading" }); setFeedback(""); setIsRefreshing(false); setPaymentRequestState("idle");
        return load(token, false, currentController.signal);
      });
    }
    openLink();
    window.addEventListener("hashchange", openLink);
    return () => { controller?.abort(); window.removeEventListener("hashchange", openLink); };
  }, []);

  async function copy() {
    try {
      await navigator.clipboard.writeText(new URL(`/invoice-links#${tokenRef.current}`, window.location.origin).href);
      setFeedback("청구서 조회 링크를 복사했습니다. 이 링크의 조회와 허용된 재시도 권한도 함께 전달됩니다.");
    } catch { setFeedback("링크를 복사하지 못했습니다. 처음 전달받은 조회 링크를 사용해 주세요."); }
  }

  const invoice = view.kind === "ready" ? view.invoice : null;
  const unready = view.kind === "ready" || view.kind === "loading" ? null : UNREADY_MESSAGES[view.kind];
  const revision = view.kind === "ready" ? view.revision : 0;
  useEffect(() => {
    if (paymentFocusRevisionRef.current !== revision) return;
    paymentFocusRevisionRef.current = null;
    paymentTitleRef.current?.focus();
  }, [revision]);
  const hasUnconfirmedPaymentRequest = paymentRequestState !== "idle";
  const canRetryPayment = invoice && !hasUnconfirmedPaymentRequest && canRetryInvoicePayment(invoice);
  const paymentState = paymentRequestState === "submitting"
    ? { label: "결제 요청 중", description: "등록된 카드로 결제를 다시 요청하고 있습니다. 결과가 나올 때까지 기다려 주세요." }
    : paymentRequestState === "unknown"
      ? { label: "결제 결과 확인 필요", description: "방금 보낸 요청의 결과를 확인하지 못했습니다. 다시 결제하지 말고 상태를 새로 조회해 주세요." }
      : invoice ? PAYMENT_STATES[invoice.payment.status] : undefined;
  function isCurrentPaymentRequest(token: string) { return tokenRef.current === token && requestRef.current === revision; }
  function acceptPaymentResult(updated: PublicInvoice, token: string) {
    if (!isCurrentPaymentRequest(token)) return;
    const nextRevision = ++requestRef.current;
    paymentFocusRevisionRef.current = nextRevision;
    setView({ kind: "ready", invoice: updated, revision: nextRevision }); setPaymentRequestState("idle");
    setFeedback(updated.payment.status === "paid" ? "결제가 완료되었습니다."
      : updated.payment.status === "failed" ? "결제에 실패했습니다. 표시된 상태와 재시도 안내를 확인해 주세요."
        : "결제 요청을 접수했습니다. 결과를 확인할 때까지 다시 결제하지 마세요.");
  }
  return <main id="main-content" className={styles.frame}>
    <header className={styles.brand}><strong>MeterEngine</strong><span>청구서 조회</span></header>
    {view.kind === "loading" ? <section className={styles.message} aria-live="polite" aria-busy="true"><h1>청구서를 불러오고 있습니다</h1><p>확정 청구와 최신 결제 상태를 확인하고 있습니다.</p></section>
      : !invoice ? <section className={styles.message}>
        <h1>{unready?.title}</h1>
        <p>{unready?.description}</p>
        {view.kind === "unavailable" && <button type="button" onClick={() => load(tokenRef.current)}>다시 조회</button>}
      </section> : <article className={styles.invoice} aria-busy={isRefreshing}>
        <div className={styles.heading}><div><h1>{formatKoreanMonth(invoice.month)} 청구서</h1><p>{invoice.customer_name}님께 {invoice.organization_name}에서 발행한 청구서입니다.</p></div><div className={styles.actions}><button type="button" onClick={() => load(tokenRef.current, true)} disabled={isRefreshing || paymentRequestState === "submitting"}>{isRefreshing ? "조회 중" : "결제 상태 새로 조회"}</button><button type="button" onClick={copy}>조회 링크 복사</button></div></div>
        {invoice.payment.mode === "test" && <p className={styles.testNotice}>테스트 모드의 청구 내역입니다. 실제 결제 내역과 구분해 확인해 주세요.</p>}
        <p role="status" className={styles.feedback} style={feedback ? undefined : { margin: 0 }}>{feedback}</p>
        <dl className={styles.details}><div><dt>청구서 번호</dt><dd>{invoice.invoice_number}</dd></div><div><dt>확정 시각 (KST)</dt><dd>{formatKstDateTime(invoice.finalized_at)}</dd></div><div><dt>공급자</dt><dd>{invoice.organization_name}</dd></div><div><dt>고객</dt><dd>{invoice.customer_name}</dd></div></dl>
        <section className={styles.section} aria-labelledby="invoice-items-title"><h2 id="invoice-items-title">청구 항목</h2>
          <p id="invoice-line-scroll-hint" className={styles.scrollHint}>단가와 공급가액은 표를 좌우로 이동해 확인할 수 있습니다.</p>
          <div className={styles.tableScroll} role="region" aria-label="청구 항목 상세" aria-describedby="invoice-line-scroll-hint" tabIndex={0}><table><caption className={styles.srOnly}>확정 시점의 수량과 단가, 항목별 공급가액</caption><thead><tr><th scope="col">항목</th><th scope="col">수량</th><th scope="col">단가</th><th scope="col">공급가액</th></tr></thead><tbody>{invoice.lines.map((line, index) => <tr key={index}><th scope="row"><strong>{LINE_NAMES[line.line_type ?? ""] ?? line.billable_metric_code ?? "사용량"}</strong>{Object.keys(line.dimension_values).length > 0 && <small>{formatDimensionValues(line.dimension_values)}</small>}{["graduated_tier", "volume"].includes(line.line_type ?? "") && line.tier_index != null && <small>구간 {line.tier_index + 1}</small>}</th><td>{formatDecimal(line.quantity)}{formatQuantityBasis(line.quantity_numerator, line.quantity_denominator) && <small>{formatQuantityBasis(line.quantity_numerator, line.quantity_denominator)}</small>}</td><td>{formatKrw(line.unit_price)}</td><td>{formatKrw(line.amount)}</td></tr>)}</tbody></table></div>
          <dl className={styles.totals}><div><dt>공급가액</dt><dd>{formatKrw(invoice.supply_amount)}</dd></div><div><dt>부가세</dt><dd>{formatKrw(invoice.tax_amount)}</dd></div><div className={styles.total}><dt>확정 청구액</dt><dd>{formatKrw(invoice.total_amount)}</dd></div></dl>
          <p className={styles.hint}>수량과 금액은 확정 시점의 기록입니다. 이후 요금 변경이나 결제 취소로 바뀌지 않습니다. 이 화면은 세금계산서나 결제 영수증이 아닙니다.</p>
        </section>
        <section className={styles.section} aria-labelledby="invoice-payment-title"><h2 id="invoice-payment-title" ref={paymentTitleRef} tabIndex={-1}>결제 내역</h2><p className={styles.status}>{paymentState?.label ?? "결제 상태 확인 필요"}</p><p>{canRetryPayment ? "결제가 완료되지 않았습니다. 아래에서 금액을 확인한 뒤 등록된 카드로 다시 시도할 수 있습니다." : paymentState?.description ?? "결제 상태를 확인하지 못했습니다. 청구서를 보낸 담당자에게 문의해 주세요."}</p>
          <dl className={styles.paymentAmounts}><div><dt>취소 제외 납부액</dt><dd>{hasUnconfirmedPaymentRequest || invoice.payment.paid_amount == null ? "확인 중" : formatKrw(invoice.payment.paid_amount)}</dd></div><div><dt>취소 완료액</dt><dd>{formatKrw(invoice.payment.canceled_amount)}</dd></div><div><dt>결제 대기액</dt><dd>{hasUnconfirmedPaymentRequest || invoice.payment.outstanding_amount == null ? "확인 중" : formatKrw(invoice.payment.outstanding_amount)}</dd></div>{invoice.payment.completed_at && <div><dt>최근 결제 처리 시각 (KST)</dt><dd>{formatKstDateTime(invoice.payment.completed_at)}</dd></div>}</dl>
          <InvoicePaymentRetry key={revision} invoice={invoice} getToken={() => tokenRef.current}
            isCurrentRequest={isCurrentPaymentRequest} onResult={acceptPaymentResult}
            onStateChange={(state, token) => { if (isCurrentPaymentRequest(token)) { setPaymentRequestState(state); setFeedback(state === "submitting" ? "결제 요청을 처리하고 있습니다. 결과가 나올 때까지 다시 결제를 요청하지 마세요." : ""); } }}
            onRefresh={() => load(tokenRef.current, true)} />
        </section>
        <footer className={styles.footer}><p>청구 내용이나 결제에 대한 문의는 {invoice.organization_name} 담당자에게 해 주세요.</p><p>조회 가능 기한: {formatKstDateTime(invoice.expires_at)} KST</p><p>주소창의 주소로는 청구서를 공유할 수 없습니다. 전달하려면 위의 &quot;조회 링크 복사&quot;를 사용해 주세요. 링크를 가진 사람은 청구서를 볼 수 있고, 재시도를 허용한 링크는 실패 결제를 다시 요청할 수 있습니다.</p></footer>
      </article>}
  </main>;
}
