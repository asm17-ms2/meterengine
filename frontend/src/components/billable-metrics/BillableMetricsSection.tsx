import type { BillableMetricRowView } from "@/app/(console)/billable-metrics/state";
import { BillableMetricsFrame } from "@/components/billable-metrics/BillableMetricsFrame";
import { BillableMetricsScreen } from "@/components/billable-metrics/BillableMetricsScreen";
import { ErrorState } from "@/components/screen/ErrorState";
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

  const rows: BillableMetricRowView[] = result.data.billable_metrics.map((billableMetric) => ({
    code: billableMetric.code,
    name: billableMetric.name,
    eventType: billableMetric.event_type,
    aggregation: billableMetric.aggregation,
    targetProperty: billableMetric.target_property,
  }));

  return <BillableMetricsScreen rows={rows} />;
}
