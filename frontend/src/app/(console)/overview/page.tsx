import { Suspense } from "react";
import type { Metadata } from "next";
import { AggregateLoading } from "@/components/screen/AggregateLoading";
import { LinkButton } from "@/components/screen/LinkButton";
import { ColumnChart } from "@/components/insights/ColumnChart";
import { RankBars } from "@/components/insights/RankBars";
import insightStyles from "@/components/insights/insights.module.css";
import { WorkspaceHeader, MonthFilter } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import type { Result } from "@/lib/api/client";
import { getDashboard, getDashboardInsights, type DashboardInsightsResponse } from "@/lib/api/workspace";
import { formatKoreanMonth, formatKrw, formatKstDateTime, formatKstShortStamp } from "@/lib/format";
import { formatShare, metricAmounts, shareOfTotal, topCustomerAmounts, topCustomerSummary } from "@/lib/insights";
import { readMonth } from "@/lib/month";
import { searchKey } from "@/lib/search-key";

export const metadata: Metadata = { title: "홈" };

const HOME_DESCRIPTION = "이 달의 금액과 확인할 항목을 먼저 보세요.";
const RANK_LIMIT = 5;
const DAY_LABEL_INTERVAL = 5;
const COMPACT_NUMBER = new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 });
const COUNT = new Intl.NumberFormat("ko-KR");

export default async function OverviewPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const month = readMonth(params.month);
  // 홈 본문을 기다리지 않고 같이 불러오도록 여기서 요청을 시작한다.
  const insights = getDashboardInsights(month);
  return <Suspense key={searchKey(params)} fallback={<><WorkspaceHeader title={`홈 - ${formatKoreanMonth(month)}`} description={HOME_DESCRIPTION}><MonthFilter month={month} /></WorkspaceHeader><section className="workspace-panel"><AggregateLoading title="이 달 금액과 확인할 항목을 계산하는 중이에요" /></section></>}><Overview month={month} insights={insights} /></Suspense>;
}

async function TrendCharts({ insights }: { insights: Promise<Result<DashboardInsightsResponse>> }) {
  const result = await insights;
  if (!result.ok) return <WorkspaceError message={result.error.message} />;
  const { monthly_amounts: monthlyAmounts, daily_event_counts: dailyEventCounts } = result.data;
  const eventTotal = dailyEventCounts.reduce((sum, day) => sum + day.event_count, 0);
  const busiest = dailyEventCounts.reduce((top, day) => day.event_count > top.event_count ? day : top, { date: "", event_count: 0 });
  return <>
    <ColumnChart
      title="최근 석 달 청구액과 받은 금액"
      summary={`발행한 청구서 기준이고 부가세를 포함해요. ${formatKstShortStamp(new Date(result.data.calculated_at))}에 계산했어요.`}
      series={[{ name: "청구액", color: "var(--series-1)" }, { name: "받은 금액", color: "var(--series-2)" }]}
      columns={monthlyAmounts.map(amount => ({ key: amount.month, axisLabel: `${Number(amount.month.slice(5))}월`, name: formatKoreanMonth(amount.month), values: [amount.finalized_total_amount, amount.collected_amount] }))}
      formatValue={formatKrw}
      formatCompact={value => COMPACT_NUMBER.format(value)}
    />
    <ColumnChart
      title="날짜별 이벤트 수"
      summary={eventTotal > 0 ? `어제까지 모두 ${COUNT.format(eventTotal)}건이고, ${Number(busiest.date.slice(8))}일에 ${COUNT.format(busiest.event_count)}건으로 가장 많았어요.` : "어제까지 발생한 이벤트가 없어요. 오늘 이벤트는 내일 새벽 계산에 들어가요."}
      series={[{ name: "이벤트 수", color: "var(--series-1)" }]}
      columns={dailyEventCounts.map(day => {
        const dayOfMonth = Number(day.date.slice(8));
        return { key: day.date, axisLabel: dayOfMonth === 1 || dayOfMonth % DAY_LABEL_INTERVAL === 0 ? String(dayOfMonth) : "", name: `${dayOfMonth}일`, values: [day.event_count] };
      })}
      formatValue={value => `${COUNT.format(value)}건`}
    />
  </>;
}

async function Overview({ month, insights }: { month: string; insights: Promise<Result<DashboardInsightsResponse>> }) {
  const result = await getDashboard(month);
  if (!result.ok) return <><WorkspaceHeader title="홈" description={HOME_DESCRIPTION} /><WorkspaceError message={result.error.message} /></>;
  const data = result.data;
  const unresolved = data.unresolved_invoice_payments;
  const paymentModeName = data.payment_mode === "test" ? "테스트" : "라이브";
  const paymentModeLabel = data.payment_mode === "test" ? "테스트 환경" : "라이브 환경";
  const missingSettings = [!data.supplier_configured && "공급자 정보 미입력", !data.payment_configured && `토스페이먼츠 ${paymentModeName} 키 미등록`].filter(Boolean);
  const steps = [
    { title: "과금할 고객 연결", detail: `고객 ${data.customer_count}명`, isComplete: data.customer_count > 0, href: "/customers" },
    { title: "사용량과 요금 정의", detail: `과금 미터 ${data.billable_metric_count}개. 단가와 적용 월을 확인하세요.`, isComplete: data.billable_metric_count > 0, href: `/pricing?month=${month}` },
    { title: "API 키 발급", detail: "사용량을 보내고 고객부터 수납까지 조회할 수 있게 수집, 조회 권한으로 API 키를 발급하세요.", isComplete: data.active_api_key_count > 0, href: "/integrations" },
    { title: "공급자와 결제 설정", detail: `${missingSettings.length ? `${missingSettings.join(", ")}. ` : ""}현재 업무 환경: ${paymentModeLabel}`, isComplete: data.supplier_configured && data.payment_configured, href: "/settings" },
  ];
  const hasStarted = steps.every(step => step.isComplete) || data.invoice_payments.length > 0 || data.event_count > 0 || unresolved?.length > 0;
  const draftCustomers = data.draft_invoice.customers;
  return <>
    <WorkspaceHeader title={hasStarted ? `홈 - ${formatKoreanMonth(month)}` : "첫 청구를 준비하세요"} description={HOME_DESCRIPTION}><MonthFilter month={month} /></WorkspaceHeader>
    <section className="finance-flow" aria-label="월별 금액 흐름"><div><span>청구 예정액</span><strong>{formatKrw(data.draft_supply_amount)}</strong><small>발행 전, 공급가액</small></div><div><span>청구액</span><strong>{formatKrw(data.finalized_total_amount)}</strong><small>부가세 포함</small></div><div><span>받을 금액</span><strong>{formatKrw(data.outstanding_amount)}</strong><small>결제 전, 실패, 확인 중</small></div><div><span>받은 금액</span><strong>{formatKrw(data.collected_amount)}</strong><small>취소 {formatKrw(data.canceled_amount)} 제외</small></div></section>
    {steps.some(step => !step.isComplete) ? <section className="workspace-panel"><h2>청구 전 준비</h2><ol className="setup-list">{steps.filter(step => !step.isComplete).map(step => <li key={step.href}><div><strong>{step.title}</strong><p>{step.detail}</p></div><LinkButton href={step.href}>설정하기</LinkButton></li>)}</ol></section> : null}
    <section className="workspace-panel"><h2>확인할 항목</h2><ul className="setup-list">{data.unpriced_usage_count > 0 && <li><div><strong>단가 없는 사용량 구분 값 {data.unpriced_usage_count}개</strong><p>단가를 등록해야 청구액에 들어가요.</p></div><span className="workspace-actions"><LinkButton href={`/billing?month=${month}`}>검토</LinkButton><LinkButton href={`/event-issues?month=${month}&reason=unpriced_usage`}>이벤트 보기</LinkButton></span></li>}{data.negative_usage_count > 0 && <li><div><strong>집계값이 음수인 구분 값 {data.negative_usage_count}개</strong><p>집계값이 음수인 구분 값은 청구에서 빠져요.</p></div><LinkButton href={`/billing?month=${month}`}>확인</LinkButton></li>}{data.failed_payment_count > 0 && <li><div><strong>결제 실패 {data.failed_payment_count}건</strong><p>카드와 실패 사유를 확인한 뒤 다시 요청하세요.</p></div><LinkButton href={`/payments?month=${month}&payment_status=failed`}>수납 확인</LinkButton></li>}</ul>{data.unpriced_usage_count === 0 && data.negative_usage_count === 0 && data.failed_payment_count === 0 && <p role="status">선택한 달에 확인할 항목이 없어요.</p>}<div><LinkButton href={`/events?month=${month}`}>수집 이벤트 {data.event_count.toLocaleString("ko-KR")}건 보기</LinkButton></div></section>
    <section className="workspace-panel" aria-labelledby="home-insights-title">
      <h2 id="home-insights-title">인사이트</h2>
      <p>매일 05시에 다시 계산해요. 오늘 생긴 변화는 내일 반영돼요.</p>
      <div className={insightStyles.grid}>
        <Suspense fallback={<AggregateLoading title="금액 추이와 이벤트 수를 세는 중이에요" />}><TrendCharts insights={insights} /></Suspense>
        <RankBars title="청구 예정액이 큰 고객" summary={topCustomerSummary(draftCustomers, RANK_LIMIT, data.draft_supply_amount)} rows={topCustomerAmounts(draftCustomers, RANK_LIMIT)} formatValue={formatKrw} formatShare={amount => formatShare(shareOfTotal(amount, data.draft_supply_amount))} empty="이 달에 청구할 금액이 있는 고객이 없어요." />
        <RankBars title="과금 미터별 청구 예정액" summary="어느 과금 미터가 매출을 만드는지 보여요. 공급가액 기준이에요." rows={metricAmounts(draftCustomers, RANK_LIMIT)} formatValue={formatKrw} empty="이 달에 금액이 생긴 과금 미터가 없어요." />
      </div>
    </section>
    <section className="workspace-panel" aria-labelledby="overview-unresolved-title">
      <details>
        <summary className="disclosure-summary"><h2 id="overview-unresolved-title">처리할 결제</h2> {Array.isArray(unresolved) ? `${COUNT.format(unresolved.length)}건` : ""}</summary>
        <p>선택한 달과 관계없이 결제 전, 실패, 결과 확인 중인 청구서를 모두 보여 줘요.</p>
        {Array.isArray(unresolved) ? <WorkspaceTable rows={unresolved} rowKey="invoice_id" label="전체 월 처리할 결제" columns={[{ key: "customer_name", label: "고객", hrefPrefix: "/payments/", hrefKey: "invoice_id" }, { key: "month", label: "청구 월" }, { key: "status", label: "수납 상태", isStatus: true }, { key: "amount", label: "청구 금액", isMoney: true }, { key: "next_retry_at", label: "다음 재시도", isDate: true }]} empty="처리할 결제가 없어요." /> : <WorkspaceError message="전체 월의 처리할 결제를 불러오지 못했어요." />}
      </details>
    </section>
    <section className="workspace-panel"><div className="workspace-heading"><h2>고객별 수납 현황</h2><LinkButton href={`/payments?month=${month}`}>수납과 취소</LinkButton></div><WorkspaceTable rows={data.invoice_payments.map(payment => ({ ...payment, id: payment.invoice_id }))} rowKey="invoice_id" columns={[{ key: "customer_name", label: "고객", hrefPrefix: "/payments/" }, { key: "status", label: "수납 상태", isStatus: true }, { key: "amount", label: "청구 금액", isMoney: true }, { key: "canceled_amount", label: "취소액", isMoney: true }, { key: "next_retry_at", label: "다음 재시도" }]} empty="청구서를 발행하면 고객별 수납 상태가 여기에 보여요." /></section>
    <p>{formatKstDateTime(data.calculated_at)} KST 기준.</p>
  </>;
}
