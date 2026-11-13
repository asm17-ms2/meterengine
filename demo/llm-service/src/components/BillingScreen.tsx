"use client";

import { ArrowRightOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useState } from "react";
import type { BillingLine, InvoiceView, WorkspaceSnapshot } from "@/lib/contracts";
import { errorMessage, jsonRequest, readApi } from "./client-api";
import { Notice } from "./Feedback";
import { formatDate, formatQuantity, modelName, MONEY, PAYMENT_LABELS, surfaceName } from "./format";

type RegistrationView = { url: string; expiresAt: string; mode: "test" | "live" };

function BillingLines({ lines }: { lines: BillingLine[] }) {
  if (lines.length === 0) return <p className="empty-state">아직 청구할 사용 항목이 없어요.</p>;
  return <div className="billing-lines">{lines.map((line, index) => <article key={`${line.metricCode}-${line.modelId}-${line.surface}-${index}`} className="billing-line">
    <div className="line-heading"><div><h4>{line.label}</h4>{line.modelId && <p>{modelName(line.modelId)}{line.surface ? `, ${surfaceName(line.surface)}` : ""}</p>}</div><strong>{MONEY.format(line.amount)}</strong></div>
    <dl><div><dt>{line.metricCode ? "과금 수량(100만 토큰)" : "과금 수량"}</dt><dd>{formatQuantity(line.quantity)}</dd></div><div><dt>{line.metricCode ? "단가(100만 토큰당)" : "단가"}</dt><dd>{MONEY.format(line.unitPrice)}</dd></div></dl>
    {line.quantityNumerator != null && line.quantityDenominator != null && line.quantityDenominator !== 1 && <p className="fraction-basis">계산 기준: {formatQuantity(line.quantityNumerator)} / {formatQuantity(line.quantityDenominator)}</p>}
  </article>)}</div>;
}

function InvoiceDetails({ invoice }: { invoice: InvoiceView }) {
  return <article className="invoice-record"><header><div><h3>{invoice.month} 청구서</h3><p>{formatDate(invoice.finalizedAt)} 확정</p></div><span className={`payment-label payment-label-${invoice.status}`}>{PAYMENT_LABELS[invoice.status] ?? "상태 확인 필요"}</span></header>
    <dl className="invoice-amounts"><div><dt>청구 금액</dt><dd>{MONEY.format(invoice.totalAmount)}</dd></div><div><dt>낸 금액</dt><dd>{invoice.paidAmount === null ? "확인할 수 없음" : MONEY.format(invoice.paidAmount)}</dd></div><div><dt>취소 금액</dt><dd>{invoice.canceledAmount === null ? "확인할 수 없음" : MONEY.format(invoice.canceledAmount)}</dd></div><div><dt>남은 금액</dt><dd>{invoice.amountDue === null ? "확인할 수 없음" : MONEY.format(invoice.amountDue)}</dd></div></dl>
    {invoice.canceledAmount !== null && invoice.canceledAmount > 0 && <p className="field-help">취소한 금액은 남은 금액과 따로 보여 드려요. 다시 청구하려면 별도로 처리해야 해요.</p>}
    <details><summary>요금 계산 내역</summary><BillingLines lines={invoice.lines} /><dl className="statement-totals"><div><dt>공급가액</dt><dd>{MONEY.format(invoice.supplyAmount)}</dd></div><div><dt>부가세</dt><dd>{MONEY.format(invoice.taxAmount)}</dd></div><div><dt>합계</dt><dd>{MONEY.format(invoice.totalAmount)}</dd></div></dl></details>
  </article>;
}

export function BillingScreen({ snapshot }: { snapshot: WorkspaceSnapshot }) {
  const [isRegistering, setIsRegistering] = useState(false);
  const [registration, setRegistration] = useState<RegistrationView | null>(null);
  const [error, setError] = useState("");
  const billing = snapshot.billing;
  const startRegistration = async () => {
    setIsRegistering(true); setError(""); setRegistration(null);
    try { setRegistration(await readApi<RegistrationView>("/api/payment-method", jsonRequest({}))); }
    catch (problem) { setError(errorMessage(problem, "카드 등록을 시작하지 못했어요. 잠시 뒤 다시 시도해 주세요.")); }
    finally { setIsRegistering(false); }
  };
  return <>
    {billing.state === "unavailable" && <Notice title="청구 정보 일부를 불러오지 못했어요">{billing.error || "확인한 항목만 보여 드려요. 다시 불러오면 최신 상태를 확인할 수 있어요."}</Notice>}
    <div className="billing-layout"><div>
      <section className="panel statement" aria-labelledby="draft-title"><header><div><h2 id="draft-title">청구 예정액</h2><p>아직 확정하지 않은 이번 달 사용 요금이에요.</p></div><span className="statement-status">확정 전</span></header>
        {billing.draft ? <><p className="statement-total">{MONEY.format(billing.draft.totalAmount)}<span>부가세 포함</span></p><details><summary>예정 요금 계산 내역</summary><BillingLines lines={billing.draft.lines} /><dl className="statement-totals"><div><dt>공급가액</dt><dd>{MONEY.format(billing.draft.supplyAmount)}</dd></div><div><dt>부가세</dt><dd>{MONEY.format(billing.draft.taxAmount)}</dd></div></dl></details></>
          : <p className="statement-empty">{billing.state === "unavailable" ? "청구 예정액을 확인할 수 없어요." : "이달에는 아직 청구 예정액이 없어요."}</p>}
        <p className="field-help">모델과 용도, 토큰 종류마다 줄을 나눠 계산해요. 줄마다 10원 미만을 버린 뒤 부가세 10%를 더해요. 사용량이 더 기록되면 금액이 바뀔 수 있어요.</p>
      </section>
      <section className="data-section" aria-labelledby="invoices-title"><h2 id="invoices-title">확정된 청구서</h2>{billing.invoices.length === 0 ? <p className="empty-state">{billing.state === "unavailable" ? "청구서를 확인하지 못했어요." : "이달에 확정된 청구서가 없어요."}</p> : billing.invoices.map(invoice => <InvoiceDetails key={invoice.id} invoice={invoice} />)}</section>
    </div><aside className="payment-method" aria-labelledby="payment-title"><h2 id="payment-title">결제 카드</h2>
      <p className="card-number">{billing.paymentMethod ? billing.paymentMethod.cardNumber : billing.state === "unavailable" ? "카드 정보를 확인할 수 없어요" : "등록한 카드가 없어요"}</p>
      <p>{billing.paymentMethod?.archived ? "이 카드는 자동 결제에 쓰지 않아요. 새로 쓸 카드를 등록해 주세요." : billing.paymentMethod ? "다음 청구에 쓸 카드예요. 바꾸려면 새 카드로 다시 인증해 주세요." : "청구서를 자동으로 결제할 카드를 등록할 수 있어요."}</p>
      {billing.paymentMethod && <p className="field-help">{formatDate(billing.paymentMethod.authenticatedAt)} 인증</p>}
      {billing.canRegisterPaymentMethod ? <Button onClick={() => void startRegistration()} loading={isRegistering}>{billing.paymentMethod ? "결제 카드 바꾸기" : "카드 등록"}</Button> : <p className="field-help">이 계정은 아직 카드를 등록할 수 없어요.</p>}
      {error && <p className="field-error" role="alert">{error}</p>}
      {registration && <div className="registration-ready" role="status"><strong>{registration.mode === "test" ? "테스트 카드를 등록할 수 있어요" : "실제 결제에 쓸 카드를 등록할 수 있어요"}</strong><p>다음 화면에서 동의 내용을 확인하고 카드를 인증해 주세요. {formatDate(registration.expiresAt)}까지 한 번 쓸 수 있어요.</p><a className="text-link" href={registration.url}>카드 등록 화면으로 이동 <ArrowRightOutlined aria-hidden="true" /></a></div>}
      <p className="field-help">카드를 등록해도 바로 결제하지 않아요.</p>
    </aside></div>
  </>;
}
