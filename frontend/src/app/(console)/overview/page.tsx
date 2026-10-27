import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { GuideOnly } from "@/components/shell/GuideMode";
import { getDashboard } from "@/lib/api/workspace";
import { readMonth } from "@/lib/month";
import { WorkspaceHeader, MonthFilter } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";

export default async function OverviewPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const month = readMonth((await searchParams).month);
  const result = await getDashboard(month);
  if (!result.ok) return <><WorkspaceHeader title="과금 운영" kicker="도입사 콘솔" description="사용량부터 수납까지, 이번 달의 진행 상태를 확인하세요." /><WorkspaceError message={result.error.message} /></>;
  const data = result.data;
  const steps = [
    { title: "과금할 고객 연결", detail: `${data.customer_count}명의 고객`, isComplete: data.customer_count > 0, href: "/customers" },
    { title: "사용량과 요금 정의", detail: `${data.billable_metric_count}개 미터. 단가와 적용 월을 확인하세요.`, isComplete: data.billable_metric_count > 0, href: `/pricing?month=${month}` },
    { title: "서버 연동 키 발급", detail: "ingest와 read 권한으로 수집과 고객 화면을 연결하세요.", isComplete: data.active_api_key_count > 0, href: "/integrations" },
    { title: "공급자와 결제 설정", detail: `현재 결제 모드: ${data.payment_mode === "test" ? "테스트" : "실결제"}`, isComplete: data.supplier_configured && data.payment_configured, href: "/settings" },
  ];
  const formatMoney = (value: number) => `${new Intl.NumberFormat("ko-KR").format(value)}원`;
  return <>
    <WorkspaceHeader title={(steps.every(step => step.isComplete) || data.invoice_payments.length > 0 || data.event_count > 0) ? "이번 달 과금 운영" : "첫 청구를 준비하세요"} kicker="도입사 콘솔" description="설정과 예외를 먼저 확인하고, 청구와 수납을 이어 진행하세요."><MonthFilter month={month} /></WorkspaceHeader>
    <section className="finance-flow" aria-label="월별 금액 흐름"><div><span>청구 예정액</span><strong>{formatMoney(data.draft_supply_amount)}</strong><small>미확정 고객, 공급가액</small></div><div><span>확정 청구액</span><strong>{formatMoney(data.finalized_total_amount)}</strong><small>세금 포함</small></div><div><span>결제 대기액</span><strong>{formatMoney(data.outstanding_amount)}</strong><small>미결제와 결과 확인 중, 취소 별도</small></div><div><span>순수납액</span><strong>{formatMoney(data.collected_amount)}</strong><small>취소 {formatMoney(data.canceled_amount)} 제외</small></div></section>
    {steps.some(step => !step.isComplete) ? <section className="workspace-panel"><h2>청구 전 준비</h2><ol className="setup-list">{steps.filter(step => !step.isComplete).map(step => <li key={step.href}><div><strong>{step.title}</strong><p>{step.detail}</p></div><Link href={step.href}>설정하기</Link></li>)}</ol></section> : <GuideOnly><section className="workspace-panel"><h2>시작 설정 확인</h2><ol className="setup-list">{steps.map(step => <li key={step.href}><div><strong>{step.title}</strong><p>{step.detail}</p></div><Link href={step.href}>관리</Link></li>)}</ol></section></GuideOnly>}
    <section className="workspace-panel"><h2>확인할 항목</h2><ul className="setup-list">{data.unpriced_usage_count > 0 && <li><div><strong>단가 없는 사용량 {data.unpriced_usage_count}개 조합</strong><p>단가를 등록해야 청구액에 포함됩니다.</p></div><Link href={`/billing?month=${month}`}>검토</Link></li>}{data.negative_usage_count > 0 && <li><div><strong>음수 합계 {data.negative_usage_count}개 조합</strong><p>합계가 음수인 조합은 0원으로 계산합니다.</p></div><Link href={`/billing?month=${month}`}>확인</Link></li>}{data.failed_payment_count > 0 && <li><div><strong>결제 실패 {data.failed_payment_count}건</strong><p>카드와 실패 원인을 확인한 뒤 다시 요청하세요.</p></div><Link href={`/payments?month=${month}`}>수납 확인</Link></li>}</ul>{data.unpriced_usage_count === 0 && data.negative_usage_count === 0 && data.failed_payment_count === 0 && <p role="status">현재 조치가 필요한 항목이 없습니다.</p>}<Link href={`/events?month=${month}`}>수집 이벤트 {data.event_count.toLocaleString("ko-KR")}건 확인</Link></section>
    <section className="workspace-panel"><div className="workspace-heading"><h2>고객별 수납 현황</h2><Link href={`/payments?month=${month}`}>결제와 취소 관리</Link></div><WorkspaceTable rows={data.invoice_payments.map(payment => ({ ...payment, id: payment.invoice_id }))} rowKey="invoice_id" columns={[{ key: "customer_name", label: "고객", hrefPrefix: "/payments/" }, { key: "status", label: "수납 상태", isStatus: true }, { key: "amount", label: "청구액", isMoney: true }, { key: "canceled_amount", label: "취소액", isMoney: true }, { key: "next_retry_at", label: "다음 재시도" }]} empty="인보이스를 확정하면 고객별 수납 상태가 표시됩니다." /></section>
    <p className="field-hint">{new Date(data.calculated_at).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })} 기준. 예정액은 공급가액, 확정액과 수납액은 세금 포함 금액입니다.</p>
  </>;
}
