import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { getDashboard } from "@/lib/api/workspace";
import { readMonth } from "@/lib/month";
import { formatKoreanMonth, formatKstDateTime } from "@/lib/format";
import { WorkspaceHeader, MonthFilter } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import type { Metadata } from "next";

export const metadata: Metadata = { title: "개요" };

export default async function OverviewPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const month = readMonth((await searchParams).month);
  const result = await getDashboard(month);
  if (!result.ok) return <><WorkspaceHeader title="개요" description="사용량부터 수납까지, 선택한 달의 진행 상태를 확인하세요." /><WorkspaceError message={result.error.message} /></>;
  const data = result.data;
  const unresolved = data.unresolved_invoice_payments;
  const paymentModeName = data.payment_mode === "test" ? "테스트" : "실결제";
  const paymentModeLabel = data.payment_mode === "test" ? "테스트 결제" : "실결제";
  const missingSettings = [!data.supplier_configured && "공급자 정보 미입력", !data.payment_configured && `토스페이먼츠 ${paymentModeName} 키 미등록`].filter(Boolean);
  const steps = [
    { title: "과금할 고객 연결", detail: `고객 ${data.customer_count}곳`, isComplete: data.customer_count > 0, href: "/customers" },
    { title: "사용량과 요금 정의", detail: `미터 ${data.billable_metric_count}개. 단가와 적용 월을 확인하세요.`, isComplete: data.billable_metric_count > 0, href: `/pricing?month=${month}` },
    { title: "API 키 발급", detail: "사용량을 보내고 고객부터 수납까지 조회할 수 있게 수집, 조회 권한으로 API 키를 발급하세요.", isComplete: data.active_api_key_count > 0, href: "/integrations" },
    { title: "공급자와 결제 설정", detail: `${missingSettings.length ? `${missingSettings.join(", ")}. ` : ""}현재 결제 모드: ${paymentModeLabel}`, isComplete: data.supplier_configured && data.payment_configured, href: "/settings" },
  ];
  const formatMoney = (value: number) => `${new Intl.NumberFormat("ko-KR").format(value)}원`;
  return <>
    <WorkspaceHeader title={(steps.every(step => step.isComplete) || data.invoice_payments.length > 0 || data.event_count > 0 || unresolved?.length > 0) ? `${formatKoreanMonth(month)} 개요` : "첫 청구를 준비하세요"} description="설정과 확인할 항목을 먼저 보고, 청구와 수납으로 넘어가세요."><MonthFilter month={month} /></WorkspaceHeader>
    <section className="finance-flow" aria-label="월별 금액 흐름"><div><span>청구 예정액</span><strong>{formatMoney(data.draft_supply_amount)}</strong><small>확정 전 고객, 공급가액</small></div><div><span>확정 청구액</span><strong>{formatMoney(data.finalized_total_amount)}</strong><small>세금 포함</small></div><div><span>결제 대기액</span><strong>{formatMoney(data.outstanding_amount)}</strong><small>결제 전과 결과 확인 중, 취소 별도</small></div><div><span>순수납액</span><strong>{formatMoney(data.collected_amount)}</strong><small>취소 {formatMoney(data.canceled_amount)} 제외</small></div></section>
    <section className="workspace-panel" aria-labelledby="overview-unresolved-title">
      <div className="workspace-heading"><h2 id="overview-unresolved-title">미해결 결제</h2><span className="muted">{paymentModeLabel}</span></div>
      <p className="field-hint">선택한 달과 관계없이 결제 전, 실패, 결과 확인 중인 인보이스를 모두 보여 줘요.</p>
      {Array.isArray(unresolved) ? <WorkspaceTable rows={unresolved} rowKey="invoice_id" label="전체 월 미해결 결제" columns={[{ key: "customer_name", label: "고객", hrefPrefix: "/payments/", hrefKey: "invoice_id" }, { key: "month", label: "청구 월" }, { key: "status", label: "수납 상태", isStatus: true }, { key: "amount", label: "청구액", isMoney: true }, { key: "next_retry_at", label: "다음 재시도", isDate: true }]} empty="미해결 결제가 없어요." /> : <WorkspaceError message="전체 월의 미해결 결제를 불러오지 못했어요." />}
    </section>
    {steps.some(step => !step.isComplete) ? <section className="workspace-panel"><h2>청구 전 준비</h2><ol className="setup-list">{steps.filter(step => !step.isComplete).map(step => <li key={step.href}><div><strong>{step.title}</strong><p>{step.detail}</p></div><Link href={step.href}>설정하기</Link></li>)}</ol></section> : null}
    <section className="workspace-panel"><h2>확인할 항목</h2><ul className="setup-list">{data.unpriced_usage_count > 0 && <li><div><strong>단가 없는 사용량 조합 {data.unpriced_usage_count}개</strong><p>단가를 등록해야 청구액에 들어가요.</p></div><span className="workspace-actions"><Link href={`/billing?month=${month}`}>검토</Link><Link href={`/event-issues?month=${month}&reason=unpriced_usage`}>이벤트 보기</Link></span></li>}{data.negative_usage_count > 0 && <li><div><strong>합계가 음수인 조합 {data.negative_usage_count}개</strong><p>합계가 음수인 조합은 0원으로 계산해요.</p></div><Link href={`/billing?month=${month}`}>확인</Link></li>}{data.failed_payment_count > 0 && <li><div><strong>결제 실패 {data.failed_payment_count}건</strong><p>카드와 실패 원인을 확인한 뒤 다시 요청하세요.</p></div><Link href={`/payments?month=${month}&payment_status=failed`}>수납 확인</Link></li>}</ul>{data.unpriced_usage_count === 0 && data.negative_usage_count === 0 && data.failed_payment_count === 0 && <p role="status">선택한 달에 확인할 항목이 없어요.</p>}<Link href={`/events?month=${month}`}>수집 로그에서 이벤트 {data.event_count.toLocaleString("ko-KR")}건 보기</Link></section>
    <section className="workspace-panel"><div className="workspace-heading"><h2>고객별 수납 현황</h2><Link href={`/payments?month=${month}`}>수납과 취소</Link></div><WorkspaceTable rows={data.invoice_payments.map(payment => ({ ...payment, id: payment.invoice_id }))} rowKey="invoice_id" columns={[{ key: "customer_name", label: "고객", hrefPrefix: "/payments/" }, { key: "status", label: "수납 상태", isStatus: true }, { key: "amount", label: "청구액", isMoney: true }, { key: "canceled_amount", label: "취소액", isMoney: true }, { key: "next_retry_at", label: "다음 재시도" }]} empty="인보이스를 확정하면 고객별 수납 상태가 여기에 보여요." /></section>
    <p className="field-hint">{formatKstDateTime(data.calculated_at)} KST 기준. 예정액은 공급가액, 확정액과 수납액은 세금 포함 금액이에요.</p>
  </>;
}
