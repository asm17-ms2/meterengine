import { CustomersFrame } from "@/components/customers/CustomersFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export function CustomersLoading({
  query,
  capabilities,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
}) {
  return (
    <CustomersFrame query={query} capabilities={capabilities}>
      <TableSkeleton />
    </CustomersFrame>
  );
}
