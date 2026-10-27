import { readArchiveStatus } from "@/lib/list-query";
import Link from "next/link";
import { PricingWorkspace } from "@/components/pricing/PricingWorkspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError } from "@/components/workspace/WorkspaceView";
import { serverFetch, toDisplayMessage } from "@/lib/api/client";
import { listBillableMetricPrices, listPricePlans, listPriceRates } from "@/lib/api/pricing";
import { config } from "@/lib/config";
import { requireTenantSession } from "@/lib/auth/session";
import type { BillableMetricResponse } from "@/lib/api/billable-metrics";
import type { CustomerResponse } from "@/lib/api/customers";

export default async function PricingPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  await requireTenantSession();
  const params = await searchParams;
  const currentMonth = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const requestedMonth = typeof params.month === "string" ? params.month : "";
  const month = /^[0-9]{4}-(0[1-9]|1[0-2])$/.test(requestedMonth) && requestedMonth >= "0001-01" ? requestedMonth : currentMonth;
  const customerId = typeof params.customer_id === "string" && params.customer_id ? params.customer_id : undefined;
  const [metrics, customers, commonPrices, scopedPrices, commonPlans, scopedPlans] = await Promise.all([
    serverFetch<{ billable_metrics: BillableMetricResponse[] }>(config.apiBaseUrl, "/v1/billable-metrics", { status: "all" }),
    serverFetch<{ customers: CustomerResponse[] }>(config.apiBaseUrl, "/v1/customers", { status: "all" }),
    listBillableMetricPrices(month), listBillableMetricPrices(month, customerId),
    listPricePlans(undefined, "all"), listPricePlans(customerId, "all"),
  ]);
  const failed = [metrics, customers, commonPrices, scopedPrices, commonPlans, scopedPlans].find(result => !result.ok);
  if (failed && !failed.ok) return <><WorkspaceHeader kicker="요금 설계" title="무엇에 얼마를 청구할까요" description="적용 월과 고객을 정한 뒤 사용량 단가와 기본료를 설정하세요." /><WorkspaceError message={toDisplayMessage(failed.error)} /></>;
  if (!metrics.ok || !customers.ok || !commonPrices.ok || !scopedPrices.ok || !commonPlans.ok || !scopedPlans.ok) return null;
  const histories = await Promise.all(metrics.data.billable_metrics.map(async metric => ({ code: metric.code, result: await listPriceRates(metric.code, customerId, "all") })));
  const historyFailure = histories.find(history => !history.result.ok);
  return <>
    <WorkspaceHeader kicker="요금 설계" title="무엇에 얼마를 청구할까요" description="같은 사용량도 적용 월과 고객에 따라 가격이 달라집니다. 청구하기 전에 이곳에서 계산 규칙을 확인하세요."><Link href="/billable-metrics">과금 미터 관리</Link></WorkspaceHeader>
    {historyFailure && !historyFailure.result.ok && <WorkspaceError message={toDisplayMessage(historyFailure.result.error)} />}
    <PricingWorkspace
      planHistoryStatus={readArchiveStatus(typeof params.plan_status === "string" ? params.plan_status : typeof params.status === "string" ? params.status : undefined)}
      rateHistoryStatus={readArchiveStatus(typeof params.rate_status === "string" ? params.rate_status : typeof params.status === "string" ? params.status : undefined)}
      metricStatus={readArchiveStatus(typeof params.metric_status === "string" ? params.metric_status : undefined)}
      month={month} currentMonth={currentMonth} customerId={customerId ?? ""} customers={customers.data.customers} metrics={metrics.data.billable_metrics}
      commonPrices={commonPrices.data.billable_metric_prices} scopedPrices={scopedPrices.data.billable_metric_prices}
      histories={Object.fromEntries(histories.map(history => [history.code, history.result.ok ? history.result.data.price_rates : []]))}
      commonPlans={commonPlans.data.price_plans} scopedPlans={scopedPlans.data.price_plans} />
  </>;
}
