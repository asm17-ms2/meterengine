import { InvoiceFinalizeButton } from "@/components/billing/InvoiceFinalizeButton";
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
  if (!result.ok) return <InvoiceFinalizeButton disabled disabledReason="청구 예정액을 불러오지 못해 발행할 수 없어요." />;
  // 고른 고객만 담긴 응답이므로 대상 수와 금액은 월 전체 값이 있을 때만 쓴다.
  const summary = result.data.summary;

  return (
    <InvoiceFinalizeDialog
      month={month}
      monthLabel={formatKoreanMonth(month)}
      targetCount={summary?.month_billable_customer_count}
      supplyAmount={summary?.month_amount === undefined ? undefined : formatKrw(summary.month_amount)}
      isUnfinishedMonth={month >= formatKstMonth()}
      hasUnpricedUsages={summary?.month_unpriced_count === undefined ? undefined : summary.month_unpriced_count > 0}
    />
  );
}
