import { BillableMetricsFrame } from "@/components/billable-metrics/BillableMetricsFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export function BillableMetricsLoading({
  query,
  capabilities,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
}) {
  return (
    <BillableMetricsFrame query={query} capabilities={capabilities}>
      <TableSkeleton />
    </BillableMetricsFrame>
  );
}
