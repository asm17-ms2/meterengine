import { readArchiveStatus, readListQuery } from "@/lib/list-query";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { PricingWorkspace, type PricingMetric, type PricingCustomer } from "@/components/pricing/PricingWorkspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError } from "@/components/workspace/WorkspaceView";
import { DataMonths } from "@/components/screen/DataMonths";
import { serverFetch, toDisplayMessage } from "@/lib/api/client";
import { listPricePlans, listPriceRates, searchBillableMetricPrices } from "@/lib/api/pricing";
import { searchOptions } from "@/lib/api/search";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { config } from "@/lib/config";
import { requireTenantSession } from "@/lib/auth/session";
import type { BillableMetricResponse } from "@/lib/api/billable-metrics";
import type { CustomerResponse } from "@/lib/api/customers";
import { normalizeResourceQuery } from "@/lib/resource-query";

export default async function PricingPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  await requireTenantSession();
  const params = await searchParams;
  const currentMonth = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const normalized = normalizeResourceQuery("billable-metric-prices", params);
  const metricStatus = readArchiveStatus(typeof normalized.status === "string" ? normalized.status : undefined);
  const query = readListQuery(normalized, CAPABILITIES.listBillableMetricPrices, { hasMonth: true });
  const month = query.month;
  const customerId = query.customerId;
  const [prices, customerOptions, commonPlans, scopedPlans] = await Promise.all([
    searchBillableMetricPrices(query), searchOptions("customers"), listPricePlans(undefined, "all"), listPricePlans(customerId, "all"),
  ]);
  const failed = [prices, customerOptions, commonPlans, scopedPlans].find(result => !result.ok);
  if (failed && !failed.ok) return <><WorkspaceHeader kicker="요금 설계" title="무엇에 얼마를 청구할까요" description="적용 월과 고객을 정한 뒤 사용량 단가와 기본료를 설정하세요." /><WorkspaceError message={toDisplayMessage(failed.error)} /></>;
  if (!prices.ok || !customerOptions.ok || !commonPlans.ok || !scopedPlans.ok) return null;
  const requestedCode = typeof params.metric === "string" ? params.metric : "";
  const selectedCode = prices.data.items.some(metric => metric.billable_metric_code === requestedCode) ? requestedCode : prices.data.items[0]?.billable_metric_code;
  const [selectedMetric, commonPrices, history] = selectedCode ? await Promise.all([
    serverFetch<BillableMetricResponse>(config.apiBaseUrl, `/v1/billable-metrics/${encodeURIComponent(selectedCode)}`),
    searchBillableMetricPrices({ ...query, customerId: undefined, metricCode: selectedCode, q: "", sort: "code", order: "asc", page: 0 }),
    listPriceRates(selectedCode, customerId, "all"),
  ]) : [null, null, null];
  const metrics: PricingMetric[] = prices.data.items.map(metric => selectedMetric?.ok && selectedMetric.data.code === metric.billable_metric_code ? { ...selectedMetric.data, rate_count: metric.rate_count } : ({ code: metric.billable_metric_code, name: metric.billable_metric_name, dimension_properties: metric.dimension_properties, archived_at: metric.archived_at, rate_count: metric.rate_count }));
  const customers: PricingCustomer[] = customerOptions.data.items.map(option => ({ id: option.value, name: option.label, is_archived: option.archived }));
  if (customerId && !customers.some(customer => customer.id === customerId)) {
    const selected = await serverFetch<CustomerResponse>(config.apiBaseUrl, `/v1/customers/${encodeURIComponent(customerId)}`);
    if (selected.ok) customers.unshift(selected.data);
  }
  const detailFailure = [selectedMetric, commonPrices, history].find(result => result && !result.ok);
  return <>
    <WorkspaceHeader kicker="요금 설계" title="무엇에 얼마를 청구할까요" description="같은 사용량도 적용 월과 고객에 따라 가격이 달라집니다. 청구하기 전에 이곳에서 계산 규칙을 확인하세요."><Link href="/billable-metrics">과금 미터 관리</Link></WorkspaceHeader>
    <DataMonths filterForm="resource-search-billable-metric-prices" resource="billable-metric-prices" pathname="/pricing" query={query} />
    {detailFailure && !detailFailure.ok && <WorkspaceError message={toDisplayMessage(detailFailure.error)} />}
    <PricingWorkspace query={query} total={prices.data.total} summary={prices.data.summary}
      planHistoryStatus={readArchiveStatus(typeof params.plan_status === "string" ? params.plan_status : typeof params.status === "string" ? params.status : undefined)}
      rateHistoryStatus={readArchiveStatus(typeof params.rate_status === "string" ? params.rate_status : typeof params.status === "string" ? params.status : undefined)}
      metricStatus={metricStatus} month={month} currentMonth={currentMonth} customerId={customerId ?? ""} customers={customers} metrics={metrics}
      commonPrices={commonPrices?.ok ? commonPrices.data.items : []} scopedPrices={prices.data.items}
      histories={selectedCode ? { [selectedCode]: history?.ok ? history.data.price_rates : [] } : {}}
      commonPlans={commonPlans.data.price_plans} scopedPlans={scopedPlans.data.price_plans} />
  </>;
}
