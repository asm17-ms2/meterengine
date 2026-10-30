import { InvoiceFinalizeButton } from "@/components/billing/InvoiceFinalizeButton";
import { InvoiceFinalizeDialog } from "@/components/billing/InvoiceFinalizeDialog";
import {
  hasUnpricedUsages,
  listFinalizeTargets,
  type DraftInvoiceResponse,
} from "@/lib/api/billing";
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
  if (!result.ok) return <InvoiceFinalizeButton disabled disabledReason="청구 예정액을 불러오지 못해 확정할 수 없습니다." />;
  const targets = listFinalizeTargets(result.data.customers);

  return (
    <InvoiceFinalizeDialog
      month={month}
      monthLabel={formatKoreanMonth(month)}
      targetCount={result.data.summary ? result.data.summary.month_billable_customer_count : targets.length}
      supplyAmount={formatKrw(result.data.summary ? result.data.summary.month_amount : targets.reduce((sum, customer) => sum + customer.amount, 0))}
      isUnfinishedMonth={month >= formatKstMonth()}
      hasUnpricedUsages={result.data.summary ? result.data.summary.month_unpriced_count > 0 : hasUnpricedUsages(targets)}
    />
  );
}
