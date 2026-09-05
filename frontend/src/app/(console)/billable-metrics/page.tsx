import { Suspense } from "react";

import {
  BillableMetricsLoading,
  BillableMetricsSection,
} from "@/components/billable-metrics/BillableMetricsSection";
import { listBillableMetrics } from "@/lib/api/billable-metrics";
import { readDevState } from "@/lib/dev-state";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function BillableMetricsPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const devState = readDevState(params.state);

  const billableMetrics = devState === "loading" ? null : listBillableMetrics(devState);

  if (!billableMetrics) return <BillableMetricsLoading />;

  return (
    <Suspense fallback={<BillableMetricsLoading />}>
      <BillableMetricsSection billableMetrics={billableMetrics} />
    </Suspense>
  );
}
