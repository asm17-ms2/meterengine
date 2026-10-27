"use client";

import { Alert, Button, Card, Empty, Flex, Tag, Typography } from "antd";
import type { CustomerResponse, InvoiceLineResponse, InvoicePaymentResponse } from "@meterengine/sdk";
import Link from "next/link";
import { useActionState } from "react";

import { createPaymentMethodRegistrationAction, type RegistrationState } from "@/app/customers/actions";
import type { CustomerPortalData } from "@/lib/customer-portal";

const PAYMENT_LABELS: Record<InvoicePaymentResponse["status"], string> = {
  not_required: "납부할 금액 없음", unpaid: "납부 대기", pending: "결제 확인 중", paid: "납부 완료", failed: "결제 실패", partially_canceled: "일부 취소", canceled: "전액 취소",
};
const MONEY = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 0 });
const QUANTITY = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 12 });
const AGGREGATION_LABELS: Record<string, string> = { sum: "합계", count: "이벤트 수", count_distinct: "고유 값 수", min: "최솟값", max: "최댓값", avg: "평균", first: "첫 값", last: "마지막 값", median: "중앙값", percentile: "백분위수" };

function quantityBasis(line: InvoiceLineResponse): string | null {
  const numerator = line.quantity_numerator;
  const denominator = line.quantity_denominator;
  return numerator != null && denominator != null && denominator > 0 && denominator !== 1 && !Number.isInteger(numerator / denominator) ? `계산 기준: ${numerator} / ${denominator}` : null;
}

function formatQuantity(value: number): string {
  return value !== 0 && Math.abs(value) < 0.0000000000005 ? value.toExponential(10) : QUANTITY.format(value);
}

type Props = { names: string[]; target: string; customers: CustomerResponse[]; month: string; data: CustomerPortalData | null; isOperatorView?: boolean };

function RegistrationPanel({ target, data }: { target: string; data: CustomerPortalData }) {
  const [state, action, isPending] = useActionState<RegistrationState, FormData>(createPaymentMethodRegistrationAction, { status: "idle" });
  return <Card title="자동결제 카드" className="portal-card">
    <Flex vertical gap={16}>
      <div><Typography.Text strong>{!data.hasPaymentMethodData ? "등록한 카드를 확인할 수 없습니다" : data.paymentMethod === null ? "등록된 카드가 없습니다" : `${data.paymentMethod.card_number}${data.paymentMethod.archived_at ? " (보관)" : ""}`}</Typography.Text><br /><Typography.Text type="secondary">{data.paymentMethod?.archived_at ? "보관된 카드는 새 자동결제에 사용하지 않습니다. 다시 사용하려면 카드 인증을 완료하세요." : data.paymentMethod ? "다음 청구부터 사용할 카드를 바꿀 수 있습니다." : "이후 청구에 사용할 자동결제 카드를 등록하세요."}</Typography.Text></div>
      <form action={action}>
        <input type="hidden" name="target" value={target} />
        <input type="hidden" name="customer_id" value={data.customer.id} />
        <Button htmlType="submit" type="primary" loading={isPending}>{data.paymentMethod?.archived_at ? "카드 재등록" : data.paymentMethod === null ? "카드 등록 시작" : "결제 카드 변경"}</Button>
      </form>
      {state.status === "failed" && <Alert type="error" title="카드 등록을 시작하지 못했습니다" description={state.message} />}
      {state.status === "ready" && <Alert type="info" title={state.mode === "test" ? "테스트 카드 등록 링크가 준비됐습니다" : "카드 등록 링크가 준비됐습니다"} description={<Flex vertical gap={8}><span>동의 내용을 확인하고 카드 인증을 완료하세요. {new Date(state.expiresAt).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })}까지 한 번 사용할 수 있습니다.</span><a href={state.url}>카드 등록 화면으로 이동</a></Flex>} />}
    </Flex>
  </Card>;
}

export function CustomerPortal({ names, target, customers, month, data, isOperatorView = false }: Props) {
  const billed = data?.invoices.reduce((sum, invoice) => sum + invoice.total_amount, 0) ?? 0;
  const paid = data?.payments.filter((payment) => ["paid", "partially_canceled"].includes(payment.status)).reduce((sum, payment) => sum + payment.amount - payment.canceled_amount, 0) ?? 0;
  return <section className="customer-portal">
    <div className="portal-context"><span>{isOperatorView ? "시연 운영: 고객 화면 설정" : "도입사 고객 화면"}</span>{isOperatorView && <Link href={`/customers?${new URLSearchParams({ target, customer_id: data?.customer.id ?? "", month })}`}>선택한 고객 화면 열기</Link>}</div>
    <header className="portal-header"><p className="portal-eyebrow">사용량과 납부 내역</p><Typography.Title level={1} style={{ margin: "8px 0" }}>{data?.customer.name ?? "고객 선택"}</Typography.Title><Typography.Text type="secondary">사용한 만큼의 요금과 결제 상태를 한곳에서 확인하세요.</Typography.Text></header>
    {isOperatorView && <section className="demo-operator-controls"><h2>시연 대상 선택</h2><p>이 설정은 시연 운영 화면에만 표시합니다. 고객 화면에는 선택한 고객의 사용량과 납부 내역만 표시합니다.</p><form key={`${target}/${data?.customer.id}/${month}`} action="/customers" method="get" className="portal-filters">
      <input type="hidden" name="view" value="operator" />
      <label>연동 대상<select name="target" defaultValue={target} onChange={(event) => { const form = event.currentTarget.form; const customer = form?.elements.namedItem("customer_id"); if (customer instanceof HTMLSelectElement) customer.value = ""; form?.requestSubmit(); }}>{names.map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
      <label>고객<select name="customer_id" defaultValue={data?.customer.id ?? ""}><option value="" disabled>고객 선택</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name}</option>)}</select></label>
      <label>청구 월<input type="month" name="month" defaultValue={month} required /></label>
      <Button htmlType="submit">내역 보기</Button>
    </form></section>}
    {!isOperatorView && data && <form key={`${target}/${data.customer.id}/${month}`} action="/customers" method="get" className="portal-filters" aria-label="조회할 청구 월">
      <input type="hidden" name="target" value={target} />
      <input type="hidden" name="customer_id" value={data.customer.id} />
      <label>청구 월<input type="month" name="month" defaultValue={month} required /></label>
      <Button htmlType="submit">내역 보기</Button>
    </form>}
    {data === null ? <Card><Empty description="아직 고객이 없습니다. 도입사 콘솔에서 고객을 등록한 뒤 다시 불러오세요." /></Card> : <>
      {data.errors.map((error, index) => <Alert key={index} type="warning" title={error} showIcon />)}
      <div className="portal-statement" aria-label="청구 요약">
        <div><p>{month} 현재 청구 예정</p><strong>{!data.hasDraftData ? "확인 필요" : data.draft === null ? "청구 예정 없음" : MONEY.format(data.draft.amount)}</strong><span>{!data.hasDraftData ? "청구 예정액을 불러오지 못했습니다" : data.draft === null ? "확정 내역은 아래에서 확인하세요" : "현재 집계 기준, 부가세 별도"}</span></div>
        <div><p>확정 청구 금액</p><strong>{data.hasInvoiceData ? MONEY.format(billed) : "확인 필요"}</strong><span>부가세 포함</span></div>
        <div><p>납부 완료 금액</p><strong>{data.hasInvoiceData && data.hasPaymentData ? MONEY.format(paid) : "확인 필요"}</strong><span>취소 금액 차감</span></div>
      </div>
      <div className="portal-body">
        <div className="portal-main-column">
          <Card title={`${month} 현재 사용량`} className="portal-card">{!data.hasUsageData ? <Typography.Text>사용량을 불러오지 못했습니다. 화면을 다시 불러오세요.</Typography.Text> : data.usages.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`${month} 사용량이 없습니다`} /> : <><ul className="portal-usage">{data.usages.map((usage) => <li key={usage.code}><div><strong>{usage.name}</strong><span>{usage.code} / {AGGREGATION_LABELS[usage.aggregation] ?? usage.aggregation}{usage.aggregation === "percentile" ? ` P${usage.percentile}` : ""}</span>{(usage.unit_size ?? 1) !== 1 && <span>집계값을 {QUANTITY.format(usage.unit_size!)}로 나눈 수량</span>}{usage.raw_quantity != null && usage.raw_quantity !== usage.quantity && <span>단위 / 반올림 적용 전 {formatQuantity(usage.raw_quantity)}</span>}</div><strong className="portal-quantity">{formatQuantity(usage.quantity)}</strong></li>)}</ul><Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>고객 전체의 집계값입니다. 조합별로 계산한 청구 수량과 다를 수 있으며, 요금 계산 내역에서 청구 수량을 확인할 수 있습니다.</Typography.Paragraph></>}</Card>
          <Card title="확정 시점의 청구와 납부" className="portal-card">{!data.hasInvoiceData ? <Typography.Text>청구 내역을 불러오지 못했습니다. 화면을 다시 불러오세요.</Typography.Text> : data.invoices.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="아직 확정된 청구 내역이 없습니다" /> : <div className="portal-invoices">{data.invoices.map((invoice) => {
            const payment = data.payments.find((candidate) => candidate.invoice_id === invoice.id);
            return <article className="portal-invoice" key={invoice.id}><Flex justify="space-between" align="start" gap={12}><div><Typography.Text strong>{invoice.month} 사용료</Typography.Text><p className="portal-date">{new Date(invoice.finalized_at).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" })} 확정</p></div><div style={{ textAlign: "right" }}><strong>{MONEY.format(invoice.total_amount)}</strong><br /><Tag color={payment?.status === "paid" ? "green" : payment?.status === "failed" ? "red" : "default"}>{payment === undefined ? "납부 상태 확인 불가" : PAYMENT_LABELS[payment.status]}</Tag></div></Flex>{payment?.last_failure_code && <Alert type="warning" title="결제가 완료되지 않았습니다" description={payment.next_retry_at ? `카드를 확인해 주세요. 다음 재시도: ${new Date(payment.next_retry_at).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })}` : "등록한 카드를 확인하거나 다른 카드로 변경해 주세요."} />}{payment && payment.canceled_amount > 0 && <p>취소된 금액 {MONEY.format(payment.canceled_amount)}</p>}<details><summary>요금 계산 내역 보기</summary><table className="portal-lines"><thead><tr><th>항목</th><th>수량</th><th>금액</th></tr></thead><tbody>{invoice.lines.map((line, index) => <tr key={index}><td>{line.line_type === "base_fee" ? "기본료" : line.line_type === "minimum_adjustment" ? "최소 과금 보정" : line.billable_metric_code}</td><td>{formatQuantity(line.quantity)}{quantityBasis(line) && <Typography.Text type="secondary" style={{ display: "block", overflowWrap: "anywhere", fontSize: 12 }}>{quantityBasis(line)}</Typography.Text>}</td><td>{MONEY.format(line.amount)}</td></tr>)}<tr><td colSpan={2}>부가세</td><td>{MONEY.format(invoice.tax_amount)}</td></tr></tbody></table></details></article>;
          })}</div>}</Card>
        </div>
        <aside><RegistrationPanel key={`${target}/${data.customer.id}`} target={target} data={data} />{data.draft && (data.draft.unpriced_usages.length > 0 || data.draft.negative_usages.length > 0) && <Alert style={{ marginTop: 16 }} type="info" title="청구 금액을 확인하고 있습니다" description="요금이 설정되지 않았거나 정정이 반영된 사용량이 있습니다. 문의할 때 고객 이름과 청구 월을 알려주세요." />}</aside>
      </div>
    </>}
  </section>;
}
