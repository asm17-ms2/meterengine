import { ExpandControls } from "@/components/table/ExpandControls";
import type { DraftInvoiceResponse } from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";

export async function BillingExpandControls({
  draftInvoice,
  isMonthFinalized,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
  isMonthFinalized: Promise<boolean>;
}) {
  const [draft, isFinalized] = await Promise.all([draftInvoice, isMonthFinalized]);
  const hasVisibleRows = draft.ok && draft.data.customers.some(customer => customer.lines.length > 0 || customer.unpriced_usages.length > 0 || (customer.negative_usages?.length ?? 0) > 0);
  return isFinalized || !hasVisibleRows ? null : <ExpandControls />;
}
