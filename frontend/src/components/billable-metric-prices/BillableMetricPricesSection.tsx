import type {
  BillableMetricPriceRowView,
  PriceRateRowView,
} from "@/app/(console)/billable-metric-prices/state";
import { BillableMetricPricesFrame } from "@/components/billable-metric-prices/BillableMetricPricesFrame";
import { BillableMetricPricesScreen } from "@/components/billable-metric-prices/BillableMetricPricesScreen";
import { ErrorState } from "@/components/screen/ErrorState";
import type {
  BillableMetricPricePriceRate,
  ListBillableMetricPricesResponse,
} from "@/lib/api/billable-metric-prices";
import type { ListBillableMetricsResponse } from "@/lib/api/billable-metrics";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import { formatDimensionValues, formatKrw } from "@/lib/format";
import type { ListQuery } from "@/lib/list-query";

function toPriceRateRow(priceRate: BillableMetricPricePriceRate): PriceRateRowView {
  return {
    dimensionValues: priceRate.dimension_values,
    combination: formatDimensionValues(priceRate.dimension_values),
    unitPrice: formatKrw(priceRate.unit_price),
  };
}

export async function BillableMetricPricesSection({
  query,
  billableMetricPrices,
  billableMetrics,
}: {
  query: ListQuery;
  billableMetricPrices: Promise<Result<ListBillableMetricPricesResponse>>;
  billableMetrics: Promise<Result<ListBillableMetricsResponse>>;
}) {
  const [pricesResult, metricsResult] = await Promise.all([billableMetricPrices, billableMetrics]);

  if (!pricesResult.ok) {
    return (
      <BillableMetricPricesFrame query={query} capabilities={CAPABILITIES.listBillableMetricPrices}>
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
      priceRates: price.price_rates.map(toPriceRateRow),
      status: price.price_rates.length === 0 ? "no_rate" : "billing",
    }),
  );

  return (
    <BillableMetricPricesScreen query={query} rows={rows} total={pricesResult.data.total} />
  );
}
