import type { MetricRowView } from "@/app/(console)/metrics/state";
import { MetricsScreen } from "@/components/metrics/MetricsScreen";
import { ErrorState } from "@/components/screen/ErrorState";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import type { Result } from "@/lib/api/client";
import type { MetricList } from "@/lib/api/metrics";

export async function MetricsSection({
  metrics,
}: {
  metrics: Promise<Result<MetricList>>;
}) {
  const result = await metrics;

  if (!result.ok) {
    return (
      <MetricsFrame>
        <ErrorState
          title="미터 목록을 불러오지 못했습니다"
          error={result.error}
        />
      </MetricsFrame>
    );
  }

  const rows: MetricRowView[] = result.data.metrics.map((entry) => ({
    code: entry.code,
    name: entry.name,
    eventType: entry.event_type,
    aggregation: entry.aggregation,
    targetProperty: entry.target_property,
  }));

  return <MetricsScreen rows={rows} />;
}

export function MetricsLoading() {
  return (
    <MetricsFrame>
      <TableSkeleton />
    </MetricsFrame>
  );
}

function MetricsFrame({ children }: { children: React.ReactNode }) {
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
