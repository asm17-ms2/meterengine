import { Suspense } from "react";

import {
  MetricsLoading,
  MetricsSection,
} from "@/components/metrics/MetricsSection";
import { loadMetrics } from "@/lib/api/metrics";
import { readDevState } from "@/lib/dev-state";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function MetricsPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const devState = readDevState(params.state);

  const metrics = devState === "loading" ? null : loadMetrics(devState);

  if (!metrics) return <MetricsLoading />;

  return (
    <Suspense fallback={<MetricsLoading />}>
      <MetricsSection metrics={metrics} />
    </Suspense>
  );
}
