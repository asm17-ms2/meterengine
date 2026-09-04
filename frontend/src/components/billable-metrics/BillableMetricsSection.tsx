import type { BillableMetricRowView } from "@/app/(console)/billable-metrics/state";
import { BillableMetricsScreen } from "@/components/billable-metrics/BillableMetricsScreen";
import { ErrorState } from "@/components/screen/ErrorState";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import type { Result } from "@/lib/api/client";
import type { ListBillableMetricsResponse } from "@/lib/api/billable-metrics";

export async function BillableMetricsSection({
  billableMetrics,
}: {
  billableMetrics: Promise<Result<ListBillableMetricsResponse>>;
}) {
  const result = await billableMetrics;

  if (!result.ok) {
    return (
      <BillableMetricsFrame>
        <ErrorState
          title="미터 목록을 불러오지 못했습니다"
          error={result.error}
        />
      </BillableMetricsFrame>
    );
  }

  const rows: BillableMetricRowView[] = result.data.billable_metrics.map((entry) => ({
    code: entry.code,
    name: entry.name,
    eventType: entry.event_type,
    aggregation: entry.aggregation,
    targetProperty: entry.target_property,
  }));

  return <BillableMetricsScreen rows={rows} />;
}

export function BillableMetricsLoading() {
  return (
    <BillableMetricsFrame>
      <TableSkeleton />
    </BillableMetricsFrame>
  );
}

function BillableMetricsFrame({ children }: { children: React.ReactNode }) {
  return (
    <>
      <ScreenHeader title="미터" />
      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="미터 이름 검색"
          placeholder="미터 이름 검색"
          disabled
        />
      </FilterBar>
      {children}
    </>
  );
}
