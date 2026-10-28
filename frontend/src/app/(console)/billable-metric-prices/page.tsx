import type { Metadata } from "next";
import { Suspense } from "react";

import { BillableMetricPricesLoading } from "@/components/billable-metric-prices/BillableMetricPricesLoading";
import { BillableMetricPricesSection } from "@/components/billable-metric-prices/BillableMetricPricesSection";
import { listBillableMetricPrices } from "@/lib/api/billable-metric-prices";
import { listBillableMetrics } from "@/lib/api/billable-metrics";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { readListQuery } from "@/lib/list-query";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "가격" };

export default async function BillableMetricPricesPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const capabilities = CAPABILITIES.listBillableMetricPrices;
  const query = readListQuery(params, capabilities);
  const { devState } = query;
  const metricsQuery = readListQuery({}, CAPABILITIES.listBillableMetrics);

  if (devState === "loading") return <BillableMetricPricesLoading query={query} capabilities={capabilities} />;

  return (
    <Suspense fallback={<BillableMetricPricesLoading query={query} capabilities={capabilities} />}>
      <BillableMetricPricesSection
        query={query}
        billableMetricPrices={listBillableMetricPrices(query, devState)}
        billableMetrics={listBillableMetrics(metricsQuery, devState)}
      />
    </Suspense>
  );
}
