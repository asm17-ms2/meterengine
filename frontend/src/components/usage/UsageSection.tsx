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

  if (result.data.billable_metric_usages.length === 0 && (result.data.total ?? 0) > 0 && query.page > 0) return <><EmptyState title="이 페이지에는 사용량이 없어요" body={`지금 조건에 맞는 고객은 ${result.data.total}명이에요. 첫 페이지에서 다시 확인하세요.`} resetHref={buildListHref("/usage", query, { page: 0 })} resetLabel="첫 페이지 보기" /><ListPagination query={query} capabilities={capabilities} total={result.data.total} /></>;

  const emptyKind = usageEmptyKind({
    customerCount: result.data.summary?.customer_count ?? groups.length,
    totalUnfiltered: result.data.total_unfiltered ?? result.data.total ?? groups.length,
    metricLineCount: result.data.billable_metric_usages.length,
    metricCode: query.metricCode,
    hasFilter: Boolean(query.q || query.customerId || query.metricCode),
  });
  if (emptyKind === "no_customers") return <EmptyState title="등록된 고객이 없어요" body="고객을 등록하면 이 달 사용량을 고객별로 집계해요." resetHref="/customers" resetLabel="고객 화면 열기" />;
  if (emptyKind === "no_matching_customers") return <EmptyState title="조건에 맞는 고객이 없어요" body="고객과 검색 조건을 바꿔 다시 조회하세요." resetHref={buildListHref("/usage", query, { page: 0, q: "", customerId: undefined, metricCode: undefined })} />;
  if (emptyKind === "no_metric_lines") return <EmptyState title="선택한 미터의 집계 항목이 없어요" body={`${formatKoreanMonth(month)}에 선택한 미터로 집계된 사용량이 없어요.`} resetHref={buildListHref("/usage", query, { page: 0, metricCode: undefined })} resetLabel="미터 조건 지우기" />;
  if (emptyKind === "no_metrics") return <EmptyState title="등록된 미터가 없어요" body={`${formatKoreanMonth(month)}에 집계할 미터가 없어요. 미터를 등록하면 집계 항목이 생겨요.`} resetHref="/billable-metrics" resetLabel="미터 화면 열기" />;

  if (groups.length === 0) {
    return (
      <EmptyState
        title="아직 반영되지 않은 내용이 있어요"
        body="방금 등록한 고객이 집계에 들어가는 중일 수 있어요. 잠시 뒤 다시 확인하세요."
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
        청구에는 구분 값별 수량을 써요. 구분 값별 수량을 더한 값은 전체 집계값과 다를 수 있어요.
      </ScreenNote>
    </>
  );
}
