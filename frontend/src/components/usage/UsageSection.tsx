import { usageEmptyKind } from "@/lib/usage-empty-kind";
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
import { formatDecimal, formatKoreanMonth } from "@/lib/format";
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
        title="집계를 불러오지 못했어요"
        error={result.error}
        narrowerHref={`/usage?month=${shiftMonth(month, -1)}`}
      />
    );
  }

  const groups = toCustomerGroups(result.data);

  if (result.data.billable_metric_usages.length === 0 && (result.data.total ?? 0) > 0 && query.page > 0) return <><EmptyState title="이 페이지에는 사용량이 없어요" body={`지금 조건에 맞는 고객은 ${result.data.total}곳이에요. 첫 페이지에서 다시 확인하세요.`} resetHref={buildListHref("/usage", query, { page: 0 })} resetLabel="첫 페이지 보기" /><ListPagination query={query} capabilities={capabilities} total={result.data.total} /></>;

  const emptyKind = usageEmptyKind({
    customerCount: result.data.summary?.customer_count ?? groups.length,
    totalUnfiltered: result.data.total_unfiltered ?? result.data.total ?? groups.length,
    metricLineCount: result.data.billable_metric_usages.length,
    metricCode: query.metricCode,
    hasFilter: Boolean(query.q || query.customerId || query.metricCode),
  });
  if (emptyKind === "no_customers") return <EmptyState title="등록된 고객이 없어요" body="고객을 등록하면 이 달 사용량을 고객별로 집계해요." resetHref="/customers" resetLabel="고객 화면 열기" />;
  if (emptyKind === "no_matching_customers") return <EmptyState title="조건에 맞는 고객이 없어요" body="고객과 검색 조건을 바꿔 다시 조회하세요." resetHref={buildListHref("/usage", query, { page: 0, q: "", customerId: undefined, metricCode: undefined })} />;
  if (emptyKind === "no_metric_lines") return <EmptyState title="선택한 미터의 집계 라인이 없어요" body={`${formatKoreanMonth(month)}에 선택한 미터로 집계된 사용량이 없어요.`} resetHref={buildListHref("/usage", query, { page: 0, metricCode: undefined })} resetLabel="미터 조건 지우기" />;
  if (emptyKind === "no_metrics") return <EmptyState title="등록된 미터가 없어요" body={`${month}에 집계할 미터가 없어요. 미터를 등록하면 집계 라인이 생겨요.`} resetHref="/billable-metrics" resetLabel="미터 화면 열기" />;

  if (groups.length === 0) {
    return (
      <EmptyState
        title={query.customerId ? "선택한 고객의 사용량이 없어요" : "이 조건의 사용량이 없어요"}
        body={`미터 ${result.data.billable_metric_usages.length}개가 등록돼 있지만 ${formatKoreanMonth(month)}에 이 조건으로 만들어진 집계 라인이 없어요.`}
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
        사용량이 없는 미터와 고객도 줄을 지우지 않고 0으로 보여요. 청구에는 차원별 수량을 써요. 차원별 값은 조합마다 따로 계산해서 평균, 최댓값, 고유 값 수와 반올림 결과는 더해도 전체 집계값과 다를 수 있어요.
      </ScreenNote>
    </>
  );
}
