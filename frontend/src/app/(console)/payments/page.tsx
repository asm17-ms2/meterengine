import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { workspaceFetch, type InvoicePaymentResponse } from "@/lib/api/workspace";
import type { InvoiceResponse } from "@/lib/api/invoices";
import { readMonth } from "@/lib/month";
import { WorkspaceHeader, MonthFilter } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { INVOICE_PAYMENT_STATUS_LABELS } from "@/lib/invoice-payment-status";

export default async function PaymentsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const month = readMonth((await searchParams).month);
  const [invoices, payments] = await Promise.all([workspaceFetch<{ invoices: InvoiceResponse[] }>("/v1/invoices", { month }), workspaceFetch<{ invoice_payments: InvoicePaymentResponse[] }>("/v1/invoice-payments", { month })]);
  if (!invoices.ok || !payments.ok) return <WorkspaceError message={!invoices.ok ? invoices.error.message : !payments.ok ? payments.error.message : "조회하지 못했습니다."} />;
  const paymentById = new Map(payments.data.invoice_payments.map(payment => [payment.invoice_id, payment]));
  const rows = invoices.data.invoices.map(invoice => ({ ...invoice, ...paymentById.get(invoice.id) }));
  return <>
    <WorkspaceHeader title="수납과 취소" kicker="도입사 콘솔" description="청구된 금액의 결제 결과를 확인하세요. 결과 확인 중인 결제는 새로 청구하지 않습니다."><MonthFilter month={month} /></WorkspaceHeader>
    <nav className="workspace-tabs"><Link inheritFilters href={`/billing?month=${month}`}>청구 검토</Link><Link inheritFilters href={`/invoices?month=${month}`}>확정 인보이스</Link><Link inheritFilters href={`/payments?month=${month}`} aria-current="page">수납과 취소</Link></nav>
    <section className="workspace-panel"><WorkspaceTable rows={rows} columns={[{ key: "customer_name", label: "고객", hrefPrefix: "/payments/" }, { key: "status", label: "수납 상태", isStatus: true, statusLabels: INVOICE_PAYMENT_STATUS_LABELS }, { key: "total_amount", label: "청구액", isMoney: true }, { key: "canceled_amount", label: "취소액", isMoney: true }, { key: "mode", label: "결제 모드" }, { key: "last_failure_code", label: "실패 코드" }, { key: "next_retry_at", label: "다음 재시도" }]} empty="확정 인보이스가 없습니다. 청구 검토에서 금액을 확인한 뒤 확정하세요." /></section>
    <p className="field-hint">고객을 선택하면 결제 시도, 취소 내역과 등록 카드 관리로 이어집니다. 취소는 확정 인보이스 금액을 바꾸지 않습니다.</p>
  </>;
}
