import { CustomersFrame } from "@/components/customers/CustomersFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";

export function CustomersLoading() {
  return (
    <CustomersFrame>
      <TableSkeleton />
    </CustomersFrame>
  );
}
