import { BillableMetricsFrame } from "@/components/billable-metrics/BillableMetricsFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";

export function BillableMetricsLoading() {
  return (
    <BillableMetricsFrame>
      <TableSkeleton />
    </BillableMetricsFrame>
  );
}
