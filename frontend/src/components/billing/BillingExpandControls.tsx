import { ExpandControls } from "@/components/table/ExpandControls";
import { resolveIsFinalizedMonth, type DraftInvoiceResponse } from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";
import type { ListInvoicesResponse } from "@/lib/api/invoices";

export async function BillingExpandControls({
  draftInvoice,
  invoices,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
  invoices: Promise<Result<ListInvoicesResponse>>;
}) {
  const isFinalized = await resolveIsFinalizedMonth(draftInvoice, invoices);

  return isFinalized ? null : <ExpandControls />;
}
