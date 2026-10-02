import type {
  BillableMetricPriceRowView,
  BillableMetricPriceStatus,
} from "@/app/(console)/billable-metric-prices/state";
import { BillableMetricPricesFrame } from "@/components/billable-metric-prices/BillableMetricPricesFrame";
import { BillableMetricPricesScreen } from "@/components/billable-metric-prices/BillableMetricPricesScreen";
import { ErrorState } from "@/components/screen/ErrorState";
import type {
  BillableMetricPriceResponse,
  ListBillableMetricPricesResponse,
} from "@/lib/api/billable-metric-prices";
import type { ListBillableMetricsResponse } from "@/lib/api/billable-metrics";
import type { Result } from "@/lib/api/client";
import { formatKrw } from "@/lib/format";

function toStatus(price: BillableMetricPriceResponse): BillableMetricPriceStatus {
  if (price.dimension_properties === null) return "no_policy";
  if (price.unit_price === null) return "no_rate";
  return "billing";
}

export async function BillableMetricPricesSection({
  billableMetricPrices,
  billableMetrics,
}: {
  billableMetricPrices: Promise<Result<ListBillableMetricPricesResponse>>;
  billableMetrics: Promise<Result<ListBillableMetricsResponse>>;
}) {
  const [pricesResult, metricsResult] = await Promise.all([billableMetricPrices, billableMetrics]);

  if (!pricesResult.ok) {
    return (
      <BillableMetricPricesFrame>
        <ErrorState title="가격 목록을 불러오지 못했습니다" error={pricesResult.error} />
      </BillableMetricPricesFrame>
    );
  }
  const nameByCode = new Map(
    metricsResult.ok
      ? metricsResult.data.billable_metrics.map((billableMetric) => [
          billableMetric.code,
          billableMetric.name,
        ])
      : [],
  );

  const rows: BillableMetricPriceRowView[] = pricesResult.data.billable_metric_prices.map(
    (price) => ({
      code: price.billable_metric_code,
      name: nameByCode.get(price.billable_metric_code) ?? price.billable_metric_code,
      dimensionProperties: price.dimension_properties,
      unitPrice: price.unit_price === null ? null : formatKrw(price.unit_price),
      status: toStatus(price),
    }),
  );

  return <BillableMetricPricesScreen rows={rows} />;
}
