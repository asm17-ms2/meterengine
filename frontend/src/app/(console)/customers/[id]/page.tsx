import { aggregateBillableMetricUsages } from "@/lib/api/usage";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { readListQuery } from "@/lib/list-query";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { workspaceFetch, type InvoicePaymentResponse } from "@/lib/api/workspace";
import { listAllInvoices } from "@/lib/api/invoices";
import { readMonth } from "@/lib/month";
import { formatKoreanMonth } from "@/lib/format";
import { WorkspaceHeader, MonthFilter } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceTable, WorkspaceError } from "@/components/workspace/WorkspaceView";
import { ArchiveFilter } from "@/components/workspace/ArchiveFilter";
import { readArchiveStatus } from "@/lib/list-query";

type CustomerResponse = { version: number; id: string; name: string; billing_email: string | null; external_id: string | null; created_at: string; archived_at?: string | null };
type CardResponse = { customer_id: string; card_number: string; mode: string; authenticated_at: string; archived_at?: string | null };

export default async function CustomerPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<Record<string, string | undefined>> }) {
  const { id } = await params;
  const query = await searchParams;
  const month = readMonth(query.month);
  const cardStatus = readArchiveStatus(query.card_status);
  const [customer, invoices, payments, card, usages, cards] = await Promise.all([
    workspaceFetch<CustomerResponse>(`/v1/customers/${encodeURIComponent(id)}`),
    listAllInvoices(readListQuery({ month, customer_id: id }, CAPABILITIES.listInvoices, { hasMonth: true }), "normal"),
    workspaceFetch<{ invoice_payments: InvoicePaymentResponse[] }>("/v1/invoice-payments", { month, customer_id: id }),
    workspaceFetch<CardResponse>(`/v1/customers/${encodeURIComponent(id)}/payment-method`),
    aggregateBillableMetricUsages(readListQuery({ month, customer_id: id }, CAPABILITIES.aggregateBillableMetricUsages, { hasMonth: true }), "normal"),
    workspaceFetch<{ payment_methods: CardResponse[] }>("/v1/payment-methods", { status: cardStatus, customer_id: id }),
  ]);
  if (!customer.ok) return <>
    <WorkspaceHeader title={customer.error.status === 404 ? "고객을 찾을 수 없습니다" : "고객 상세"} kicker="도입사 콘솔"
      description={customer.error.status === 404 ? "주소가 잘못됐거나 다른 도입사나 업무 환경의 고객일 수 있습니다. 현재 도입사와 업무 환경을 확인하세요." : "고객 정보를 불러오지 못했습니다."}>
      <Link href="/customers">고객 목록으로</Link>
    </WorkspaceHeader>
    <WorkspaceError message={customer.error.message} status={customer.error.status} />
  </>;
  const detail = customer.data;
  const paymentById = new Map(payments.ok ? payments.data.invoice_payments.map(payment => [payment.invoice_id, payment]) : []);
  const usageRows = usages.ok ? usages.data.billable_metric_usages.flatMap(metric => metric.customers.filter(entry => entry.customer_id === id).flatMap(entry => entry.dimension_usages.map((dimension, index) => ({ id: `${metric.code}-${index}`, metric: `${metric.name} (${metric.code})`, dimension_values: Object.entries(dimension.dimension_values).map(([key, value]) => `${key}: ${value ?? "값 없음"}`).join(", ") || "모든 사용량", quantity: dimension.quantity })))) : [];
  const cardRows = cards.ok ? cards.data.payment_methods.map(method => ({ ...method, id: `${method.customer_id}-${method.mode}-${method.authenticated_at}-${method.archived_at ?? "active"}`, status: method.archived_at ? "archived" : "active" })) : [];
  return <>
    <WorkspaceHeader title={detail.name} kicker="도입사 콘솔" description="고객의 사용량과 청구, 카드와 수납을 이어 확인하세요."><MonthFilter month={month} preserve={["card_status"]} /></WorkspaceHeader>
    <section className="workspace-panel">
      {detail.archived_at && <p role="status">보관된 고객입니다. 과거 내역은 유지되며 복원 전에는 신규 업무와 사용량 수집을 할 수 없습니다.</p>}
      <dl className="detail-strip"><div><dt>외부 고객 ID</dt><dd>{detail.external_id ?? "연결하지 않음"}</dd></div><div><dt>청구 이메일</dt><dd>{detail.billing_email ?? "등록하지 않음"}</dd></div><div><dt>현재 등록 카드</dt><dd>{card.ok ? `${card.data.card_number} (${card.data.mode === "test" ? "테스트 결제" : "실결제"}${card.data.archived_at ? ", 보관" : ""})` : card.error.status === 404 ? "등록 카드 없음" : "조회 실패"}</dd></div></dl>
      <div className="workspace-actions">{!detail.archived_at && <><ActionPanel title="고객 정보 수정" operation="updateCustomer" label="저장" fields={[{ name: "version", label: "", type: "hidden", value: String(detail.version) },{ name: "id", label: "", type: "hidden", value: id }, { name: "name", label: "고객명", value: detail.name, maxLength: 255 }, { name: "billing_email", label: "청구 이메일", type: "email", value: detail.billing_email ?? "", isOptional: true, maxLength: 254 }]} /><ActionPanel title="카드 등록 링크 만들기" operation="createPaymentMethodRegistration" description="일회용 등록 링크를 생성합니다. 고객의 동의와 카드 인증 뒤에 등록이 완료됩니다." fields={[{ name: "id", label: "", type: "hidden", value: id }]} /></>}<ActionPanel title={detail.archived_at ? "고객 복원" : "고객 보관"} operation={detail.archived_at ? "restoreCustomer" : "deleteCustomer"} isDanger={!detail.archived_at} description={detail.archived_at ? "신규 업무와 사용량 수집을 다시 허용합니다. 과거 기록은 그대로 유지됩니다." : "이 고객의 신규 업무와 사용량 수집을 중단합니다. 과거 사용량, 청구와 수납 기록은 보존되며 보관 목록에서 복원할 수 있습니다."} fields={[{ name: "id", label: "", type: "hidden", value: id }, { name: "month", label: "", type: "hidden" as const, value: month }]} /></div>
      {!card.ok && card.error.status !== 404 && <WorkspaceError message={card.error.message} />}
    </section>
    <section className="workspace-panel"><h2>{formatKoreanMonth(month)} 청구와 수납</h2>{!invoices.ok ? <WorkspaceError message={invoices.error.message} /> : !payments.ok ? <WorkspaceError message={payments.error.message} /> : <WorkspaceTable rows={invoices.data.invoices.map(invoice => ({ ...invoice, ...paymentById.get(invoice.id) }))} columns={[{ key: "month", label: "청구 월", hrefPrefix: "/payments/" }, { key: "total_amount", label: "세금 포함 금액", isMoney: true }, { key: "status", label: "수납 상태", isStatus: true }, { key: "canceled_amount", label: "취소액", isMoney: true }]} empty="확정된 청구가 없습니다. 청구 검토에서 사용량과 금액을 확인하세요." />}</section>
    <section className="workspace-panel"><h2>과금 대상 사용량</h2>{usages.ok ? <WorkspaceTable rows={usageRows} columns={[{ key: "metric", label: "미터" }, { key: "dimension_values", label: "과금 조합" }, { key: "quantity", label: "수량", isNumber: true }]} empty="선택한 월에 집계된 사용량이 없습니다." /> : <WorkspaceError message={usages.error.message} />}</section>
    <section className="workspace-panel"><h2>현재 결제 모드의 카드 관리</h2><p className="field-hint">보관하면 새 자동 결제에 사용할 수 없습니다. 다시 사용하려면 고객의 동의와 카드 인증을 받아 재등록해야 합니다.</p><ArchiveFilter parameterName="card_status" label="카드 관리 상태" />{cards.ok ? <WorkspaceTable rows={cardRows} columns={[{ key: "card_number", label: "등록 카드" }, { key: "mode", label: "결제 모드" }, { key: "status", label: "관리 상태", isStatus: true }, { key: "authenticated_at", label: "인증 시각", isDate: true }, { key: "archived_at", label: "보관 시각", isDate: true }]} rowActions={Object.fromEntries(cardRows.filter(method => !method.archived_at).map(method => [method.id, [{ title: "카드 보관", operation: "archivePaymentMethod", description: `${method.card_number} (${method.mode === "test" ? "테스트 결제" : "실결제"}) 카드를 새 자동 결제에 사용하지 않습니다. 과거 결제와 취소 이력은 유지됩니다. 다시 사용하려면 고객이 카드를 재인증해야 합니다.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: id }], isDanger: true }]]))} empty={cardStatus === "archived" ? "현재 결제 모드에 보관된 카드가 없습니다." : cardStatus === "all" ? "현재 결제 모드에 등록하거나 보관한 카드가 없습니다." : "현재 결제 모드에 사용 중인 카드가 없습니다. 보관됨 또는 전체에서 이전 카드를 확인하세요."} /> : <WorkspaceError message={cards.error.message} />}</section>
    <div className="workspace-actions">{!detail.archived_at && <Link href={`/pricing?month=${month}&customer_id=${encodeURIComponent(id)}`}>고객별 요금 설계</Link>}<Link href={`/events?month=${month}&customer_id=${encodeURIComponent(id)}`}>수집 로그 확인</Link><Link href={`/customers?month=${month}${detail.archived_at ? "&status=archived" : ""}`}>고객 목록으로</Link></div>
  </>;
}
