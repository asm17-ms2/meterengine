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
import type { MonthlyListQuery } from "@/lib/list-query";
import { sumOf, toMeterItems } from "@/lib/meter-items";
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

  if (result.data.billable_metric_usages.length === 0) {
    return (
      <EmptyState
        title="등록된 미터가 없습니다"
        body={`이 도입사에 등록된 미터가 없어 ${month} 집계 라인이 만들어지지 않았습니다. 미터 설정을 확인하세요.`}
        resetHref="/usage"
      />
    );
  }

  if (groups.length === 0) {
    return (
      <EmptyState
        title="등록된 고객이 없습니다"
        body={`미터 ${result.data.billable_metric_usages.length}개가 등록돼 있지만 이 도입사에 고객이 없어 ${month} 집계 라인이 만들어지지 않았습니다. 고객을 먼저 등록하세요.`}
        resetHref="/usage"
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
      }),
      (billableMetricLines) => ({
        quantity: formatDecimal(
          sumOf(billableMetricLines.map((billableMetricLine) => billableMetricLine.quantity)),
        ),
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
      </ScreenNote>
    </>
  );
}
