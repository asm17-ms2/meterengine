import { Suspense } from "react";

import { BillableMetricPricesLoading } from "@/components/billable-metric-prices/BillableMetricPricesLoading";
import { BillableMetricPricesSection } from "@/components/billable-metric-prices/BillableMetricPricesSection";
import { listBillableMetricPrices } from "@/lib/api/billable-metric-prices";
import { listBillableMetrics } from "@/lib/api/billable-metrics";
import { readDevState } from "@/lib/dev-state";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function BillableMetricPricesPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const devState = readDevState(params.state);

  if (devState === "loading") return <BillableMetricPricesLoading />;

  return (
    <Suspense fallback={<BillableMetricPricesLoading />}>
      <BillableMetricPricesSection
        billableMetricPrices={listBillableMetricPrices(devState)}
        billableMetrics={listBillableMetrics(devState)}
      />
    </Suspense>
  );
}
