import type { BillableMetricRowView } from "@/app/(console)/billable-metrics/state";
import { BillableMetricsFrame } from "@/components/billable-metrics/BillableMetricsFrame";
import { BillableMetricsScreen } from "@/components/billable-metrics/BillableMetricsScreen";
import { ErrorState } from "@/components/screen/ErrorState";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import type { ListBillableMetricsResponse } from "@/lib/api/billable-metrics";
import type { ListQuery } from "@/lib/list-query";

export async function BillableMetricsSection({
  billableMetrics,
  query,
}: {
  billableMetrics: Promise<Result<ListBillableMetricsResponse>>;
  query: ListQuery;
}) {
  const result = await billableMetrics;

  if (!result.ok) {
    return (
      <BillableMetricsFrame query={query} capabilities={CAPABILITIES.listBillableMetrics}>
        <ErrorState
          title="미터 목록을 불러오지 못했습니다"
          error={result.error}
        />
      </BillableMetricsFrame>
    );
  }

  const currentMonth = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const rows: BillableMetricRowView[] = result.data.billable_metrics.map((billableMetric) => ({
    code: billableMetric.code,
    version: billableMetric.version,
    name: billableMetric.name,
    eventType: billableMetric.event_type,
    aggregation: billableMetric.aggregation,
    targetProperty: billableMetric.target_property,
    percentile: billableMetric.percentile,
    unitSize: billableMetric.unit_size,
    roundingMode: billableMetric.rounding_mode,
    roundingPrecision: billableMetric.rounding_precision,
    dimensionProperties: billableMetric.dimension_properties,
    archivedAt: billableMetric.archived_at,
    activeFrom: billableMetric.active_from,
    inactiveFrom: billableMetric.inactive_from,
    isReactivationScheduled: Boolean(billableMetric.active_from && billableMetric.active_from > currentMonth),
  }));

  return (
    <BillableMetricsScreen
      rows={rows}
      total={result.data.total}
      query={query}
      capabilities={CAPABILITIES.listBillableMetrics}
    />
  );
}
