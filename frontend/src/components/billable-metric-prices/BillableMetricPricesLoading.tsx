import { BillableMetricPricesFrame } from "@/components/billable-metric-prices/BillableMetricPricesFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export function BillableMetricPricesLoading({
  query,
  capabilities,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
}) {
  return (
    <BillableMetricPricesFrame query={query} capabilities={capabilities}>
      <TableSkeleton />
    </BillableMetricPricesFrame>
  );
}
