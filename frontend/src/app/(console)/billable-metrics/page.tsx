import type { Metadata } from "next";
import { Suspense } from "react";

import { BillableMetricsLoading } from "@/components/billable-metrics/BillableMetricsLoading";
import { BillableMetricsSection } from "@/components/billable-metrics/BillableMetricsSection";
import { listBillableMetrics } from "@/lib/api/billable-metrics";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { readListQuery } from "@/lib/list-query";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "미터" };

export default async function BillableMetricsPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const capabilities = CAPABILITIES.listBillableMetrics;
  const query = readListQuery(params, capabilities);
  const { devState } = query;

  const billableMetrics = devState === "loading" ? null : listBillableMetrics(query, devState);

  if (!billableMetrics) return <BillableMetricsLoading query={query} capabilities={capabilities} />;

  return (
    <Suspense fallback={<BillableMetricsLoading query={query} capabilities={capabilities} />}>
      <BillableMetricsSection billableMetrics={billableMetrics} query={query} />
    </Suspense>
  );
}
