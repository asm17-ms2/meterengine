import { BillableMetricPricesFrame } from "@/components/billable-metric-prices/BillableMetricPricesFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";

export function BillableMetricPricesLoading() {
  return (
    <BillableMetricPricesFrame>
      <TableSkeleton />
    </BillableMetricPricesFrame>
  );
}
