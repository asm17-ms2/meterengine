import { resolveIsFinalizedMonth, type DraftInvoiceResponse } from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";
import type { ListInvoicesResponse } from "@/lib/api/invoices";

export async function BillingStatusTag({
  draftInvoice,
  invoices,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
  invoices: Promise<Result<ListInvoicesResponse>>;
}) {
  const isFinalized = await resolveIsFinalizedMonth(draftInvoice, invoices);

  return (
    <span className="tag tag-outline">{isFinalized ? "확정됨 (finalized)" : "확정 전 (draft)"}</span>
  );
}
