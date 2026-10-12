import { InvoiceFinalizeDialog } from "@/components/billing/InvoiceFinalizeDialog";
import type { DraftInvoiceResponse } from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";
import { formatKoreanMonth, formatKrw } from "@/lib/format";
import { formatKstMonth } from "@/lib/month";

export async function InvoiceFinalizeSection({
  draftInvoice,
  month,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
  month: string;
}) {
  const result = await draftInvoice;
  const targets = result.ok ? result.data.customers.filter((customer) => customer.amount !== 0) : [];

  return (
    <InvoiceFinalizeDialog
      month={month}
      monthLabel={formatKoreanMonth(month)}
      targetCount={targets.length}
      supplyAmount={formatKrw(targets.reduce((sum, customer) => sum + customer.amount, 0))}
      isUnfinishedMonth={month >= formatKstMonth()}
    />
  );
}
