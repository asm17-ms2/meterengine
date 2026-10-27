import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import { ListPagination } from "@/components/screen/ListPagination";
import { ScreenNote } from "@/components/screen/ScreenNote";
import { UsageTable, type UsageGroupView } from "@/components/usage/UsageTable";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import {
  toCustomerGroups,
  type ListBillableMetricUsagesResponse,
} from "@/lib/api/usage";
import { formatDecimal } from "@/lib/format";
import { buildListHref, type MonthlyListQuery } from "@/lib/list-query";
import { toMeterItems } from "@/lib/meter-items";
import { shiftMonth } from "@/lib/month";

export async function UsageSection({
  usage,
  query,
}: {
  usage: Promise<Result<ListBillableMetricUsagesResponse>>;
  query: MonthlyListQuery;
}) {
  const { month } = query;
  const capabilities = CAPABILITIES.aggregateBillableMetricUsages;
  const result = await usage;

  if (!result.ok) {
    return (
      <ErrorState
        title="집계를 불러오지 못했습니다"
        error={result.error}
        narrowerHref={`/usage?month=${shiftMonth(month, -1)}`}
      />
    );
  }

  const groups = toCustomerGroups(result.data);

  if (result.data.billable_metric_usages.length === 0 && (result.data.total ?? 0) > 0 && query.page > 0) return <><EmptyState title="이 페이지에 사용량이 없습니다" body={`현재 조건에 맞는 고객은 ${result.data.total}곳입니다. 첫 페이지에서 다시 확인하세요.`} resetHref={buildListHref("/usage", query, { page: 0 })} resetLabel="첫 페이지 보기" /><ListPagination query={query} capabilities={capabilities} total={result.data.total} /></>;

  if (result.data.summary && result.data.summary.customer_count === 0) return <EmptyState title="조건에 맞는 고객이 없습니다" body="고객과 검색 조건을 바꿔 다시 조회하세요." resetHref={buildListHref("/usage", query, { page: 0, q: "", customerId: undefined, metricCode: undefined })} />;

  if (result.data.billable_metric_usages.length === 0) {
    return (
      <EmptyState
        title={query.metricCode ? "조건에 맞는 미터가 없습니다" : "등록된 미터가 없습니다"}
        body={`이 도입사에 등록된 미터가 없어 ${month} 집계 라인이 만들어지지 않았습니다. 미터 설정을 확인하세요.`}
        resetHref={`/usage?month=${month}`}
      />
    );
  }

  if (groups.length === 0) {
    return (
      <EmptyState
        title={query.customerId ? "선택한 고객의 사용량이 없습니다" : "등록된 고객이 없습니다"}
        body={`미터 ${result.data.billable_metric_usages.length}개가 등록돼 있지만 이 도입사에 고객이 없어 ${month} 집계 라인이 만들어지지 않았습니다. 고객을 먼저 등록하세요.`}
        resetHref={`/usage?month=${month}`}
      />
    );
  }

  const usageGroupViews: UsageGroupView[] = groups.map((group) => ({
    customerId: group.customerId,
    customerName: group.customerName,
    items: toMeterItems(
      group.customerId,
      group.billableMetricLines,
      (billableMetricLine) => billableMetricLine.label,
      (billableMetricLine) => ({
        label: billableMetricLine.label,
        labelTooltip: billableMetricLine.labelTooltip,
        dimensionValues: billableMetricLine.dimensionValues,
        quantity: formatDecimal(billableMetricLine.quantity),
        rawQuantity: billableMetricLine.rawQuantity != null && billableMetricLine.rawQuantity !== billableMetricLine.quantity ? formatDecimal(billableMetricLine.rawQuantity) : undefined,
        overallRawQuantity: billableMetricLine.overallRawQuantity != null && billableMetricLine.overallRawQuantity !== billableMetricLine.overallQuantity ? formatDecimal(billableMetricLine.overallRawQuantity) : undefined,
      }),
      (billableMetricLines) => ({
        quantity: formatDecimal(billableMetricLines[0].overallQuantity),
        amount: null,
      }),
    ),
  }));

  return (
    <>
      <UsageTable groups={usageGroupViews} query={query} capabilities={capabilities} />
      <ListPagination query={query} capabilities={capabilities} total={result.data.total} />
      <ScreenNote>
        사용량이 0인 미터와 이벤트가 0건인 고객도 0으로 표시됩니다. 계약된
        미터는 값이 없어도 라인이 유지됩니다.
        전체 집계값은 고객의 전체 이벤트에서 계산합니다. 차원별 집계값은 각 조합에서 따로 계산하므로 평균, 최댓값, 고유 값 수와 반올림 결과는 전체 집계값과 합이 다를 수 있습니다. 청구에는 차원별 수량을 적용합니다.
      </ScreenNote>
    </>
  );
}
