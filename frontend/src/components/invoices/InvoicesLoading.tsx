import { InvoicesFrame } from "@/components/invoices/InvoicesFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";

export function InvoicesLoading({ month }: { month: string | undefined }) {
  return (
    <InvoicesFrame month={month}>
      <TableSkeleton />
    </InvoicesFrame>
  );
}
