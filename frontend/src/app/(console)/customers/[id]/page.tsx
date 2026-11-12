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
import type { Metadata } from "next";

export const metadata: Metadata = { title: "고객 상세" };

type CustomerResponse = { version: number; id: string; name: string; billing_email: string | null; external_id: string | null; created_at: string; archived_at?: string | null };
type CardResponse = { customer_id: string; card_number: string; mode: string; authenticated_at: string; archived_at?: string | null };

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function CustomerLoadFailure({ message, status }: { message: string; status: number }) {
  return <>
    <WorkspaceHeader title={status === 404 ? "고객을 찾을 수 없어요" : "고객 상세"}
      description={status === 404 ? "주소가 잘못됐거나 다른 조직이나 다른 업무 환경의 고객일 수 있어요. 화면 위에서 조직과 업무 환경을 확인하세요." : "고객 정보를 불러오지 못했어요."}>
      <Link href="/customers">고객 목록으로</Link>
    </WorkspaceHeader>
    {status !== 404 && <WorkspaceError message={message} status={status} />}
  </>;
}

export default async function CustomerPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<Record<string, string | undefined>> }) {
  const { id } = await params;
  const query = await searchParams;
  const month = readMonth(query.month);
  const cardStatus = readArchiveStatus(query.card_status);
  // 고객 id 모양이 아니면 서버가 404가 아닌 400을 주므로, 묻지 않고 찾을 수 없다고 알린다.
  if (!UUID_PATTERN.test(id)) return <CustomerLoadFailure message="고객을 찾을 수 없어요" status={404} />;
  const [customer, invoices, payments, card, usages, cards] = await Promise.all([
    workspaceFetch<CustomerResponse>(`/v1/customers/${encodeURIComponent(id)}`),
    listAllInvoices(readListQuery({ month, customer_id: id }, CAPABILITIES.listInvoices, { hasMonth: true }), "normal"),
    workspaceFetch<{ invoice_payments: InvoicePaymentResponse[] }>("/v1/invoice-payments", { month, customer_id: id }),
    workspaceFetch<CardResponse>(`/v1/customers/${encodeURIComponent(id)}/payment-method`),
    aggregateBillableMetricUsages(readListQuery({ month, customer_id: id }, CAPABILITIES.aggregateBillableMetricUsages, { hasMonth: true }), "normal"),
    workspaceFetch<{ payment_methods: CardResponse[] }>("/v1/payment-methods", { status: cardStatus, customer_id: id }),
  ]);
  if (!customer.ok) return <CustomerLoadFailure message={customer.error.message} status={customer.error.status} />;
  const detail = customer.data;
  const paymentById = new Map(payments.ok ? payments.data.invoice_payments.map(payment => [payment.invoice_id, payment]) : []);
  const usageRows = usages.ok ? usages.data.billable_metric_usages.flatMap(metric => metric.customers.filter(entry => entry.customer_id === id).flatMap(entry => entry.dimension_usages.map((dimension, index) => ({ id: `${metric.code}-${index}`, metric: `${metric.name} (${metric.code})`, dimension_values: Object.entries(dimension.dimension_values).map(([key, value]) => `${key}: ${value ?? "값 없음"}`).join(", ") || "모든 사용량", quantity: dimension.quantity })))) : [];
  // customer_id를 모르는 백엔드는 모든 고객의 카드를 돌려주므로 화면에서도 거른다. 카드 보관은 이 고객 id로 나간다.
  const cardRows = cards.ok ? cards.data.payment_methods.filter(method => method.customer_id === id).map(method => ({ ...method, id: `${method.customer_id}-${method.mode}-${method.authenticated_at}-${method.archived_at ?? "active"}`, status: method.archived_at ? "archived" : "active" })) : [];
  return <>
    <WorkspaceHeader title={detail.name} description="이 고객의 사용량, 청구, 카드, 수납을 한곳에서 확인하세요."><MonthFilter month={month} preserve={["card_status"]} />{!detail.archived_at && <Link href={`/pricing?month=${month}&customer_id=${encodeURIComponent(id)}`}>고객별 요금 설계</Link>}<Link href={`/events?month=${month}&customer_id=${encodeURIComponent(id)}`}>수집 로그 확인</Link><Link href={`/customers?month=${month}${detail.archived_at ? "&status=archived" : ""}`}>고객 목록으로</Link></WorkspaceHeader>
    <section className="workspace-panel">
      {detail.archived_at && <p role="status">보관된 고객이에요. 복원하기 전에는 사용량 이벤트를 받지 않고, 고객 정보 수정과 카드 등록 링크, 고객별 요금 등록도 할 수 없어요. 지난 내역은 그대로 남아 있어요.</p>}
      <dl className="detail-strip"><div><dt>외부 고객 ID</dt><dd>{detail.external_id ?? "연결하지 않음"}</dd></div><div><dt>청구 이메일</dt><dd>{detail.billing_email ?? "등록하지 않음"}</dd></div><div><dt>현재 등록 카드</dt><dd>{card.ok ? `${card.data.card_number} (${card.data.mode === "test" ? "테스트 결제" : "실결제"}${card.data.archived_at ? ", 보관" : ""})` : card.error.status === 404 ? "등록 카드 없음" : "조회 실패"}</dd></div></dl>
      <div className="workspace-actions">{!detail.archived_at && <><ActionPanel title="고객 정보 수정" operation="updateCustomer" label="저장" fields={[{ name: "version", label: "", type: "hidden", value: String(detail.version) },{ name: "id", label: "", type: "hidden", value: id }, { name: "name", label: "고객명", value: detail.name, maxLength: 255 }, { name: "billing_email", label: "청구 이메일", type: "email", value: detail.billing_email ?? "", isOptional: true, maxLength: 254 }]} /><ActionPanel title="카드 등록 링크 만들기" triggerType={!card.ok && card.error.status === 404 ? "primary" : "default"} operation="createPaymentMethodRegistration" description="고객에게 보낼 일회용 카드 등록 링크를 만들어요. 고객이 동의하고 카드를 인증하면 등록이 끝나요." fields={[{ name: "id", label: "", type: "hidden", value: id }]} /></>}<span className="workspace-actions-end"><ActionPanel title={detail.archived_at ? "고객 복원" : "고객 보관"} operation={detail.archived_at ? "restoreCustomer" : "deleteCustomer"} isDanger={!detail.archived_at} description={detail.archived_at ? "복원하면 이 고객의 사용량 이벤트를 다시 받고, 고객 정보 수정과 카드 등록 링크, 고객별 요금 등록도 다시 할 수 있어요. 지난 기록은 그대로 남아 있어요." : "보관하면 이 고객의 사용량 이벤트를 더 받지 않고, 고객 정보 수정과 카드 등록 링크, 고객별 요금 등록도 막혀요. 지난 사용량, 청구, 수납 기록은 남고 보관 목록에서 복원할 수 있어요."} fields={[{ name: "id", label: "", type: "hidden", value: id }, { name: "month", label: "", type: "hidden" as const, value: month }]} /></span></div>
      {!card.ok && card.error.status !== 404 && <WorkspaceError message={card.error.message} />}
    </section>
    <section className="workspace-panel"><h2>{formatKoreanMonth(month)} 청구와 수납</h2>{!invoices.ok ? <WorkspaceError message={invoices.error.message} /> : !payments.ok ? <WorkspaceError message={payments.error.message} /> : <WorkspaceTable rows={invoices.data.invoices.map(invoice => ({ ...invoice, ...paymentById.get(invoice.id) }))} columns={[{ key: "month", label: "청구 월", hrefPrefix: "/payments/" }, { key: "total_amount", label: "세금 포함 금액", isMoney: true }, { key: "status", label: "수납 상태", isStatus: true }, { key: "canceled_amount", label: "취소액", isMoney: true }]} empty="확정 인보이스가 없어요. 청구 검토에서 사용량과 금액을 확인하세요." />}</section>
    <section className="workspace-panel"><h2>과금 대상 사용량</h2>{usages.ok ? <WorkspaceTable rows={usageRows} columns={[{ key: "metric", label: "미터" }, { key: "dimension_values", label: "과금 조합" }, { key: "quantity", label: "수량", isNumber: true }]} empty="선택한 달에 집계된 사용량이 없어요." /> : <WorkspaceError message={usages.error.message} />}</section>
    <section className="workspace-panel"><h2>현재 결제 모드의 카드 관리</h2><p className="field-hint">보관한 카드는 새 자동 결제에 쓰지 않아요. 다시 쓰려면 고객이 동의하고 카드를 인증해 다시 등록해야 해요.</p><ArchiveFilter parameterName="card_status" label="카드 관리 상태" />{cards.ok ? <WorkspaceTable rows={cardRows} columns={[{ key: "card_number", label: "등록 카드" }, { key: "mode", label: "결제 모드" }, { key: "status", label: "관리 상태", isStatus: true }, { key: "authenticated_at", label: "인증 시각", isDate: true }, { key: "archived_at", label: "보관 시각", isDate: true }]} rowActions={Object.fromEntries(cardRows.filter(method => !method.archived_at).map(method => [method.id, [{ title: "카드 보관", operation: "archivePaymentMethod", description: `${method.card_number} (${method.mode === "test" ? "테스트 결제" : "실결제"}) 카드를 새 자동 결제에 쓰지 않아요. 지난 결제와 취소 내역은 남아요. 다시 쓰려면 고객이 카드를 다시 인증해야 해요.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: id }], isDanger: true }]]))} empty={cardStatus === "archived" ? "현재 결제 모드에 보관된 카드가 없어요." : cardStatus === "all" ? "현재 결제 모드에 등록하거나 보관한 카드가 없어요." : "현재 결제 모드에 사용 중인 카드가 없어요. 보관됨이나 전체에서 이전 카드를 확인하세요."} /> : <WorkspaceError message={cards.error.message} />}</section>
  </>;
}
