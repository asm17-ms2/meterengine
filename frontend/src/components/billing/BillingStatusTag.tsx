import { StatusTag } from "@/components/screen/StatusTag";
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
    <StatusTag color={isFinalized ? "success" : "processing"}>
      {isFinalized ? "발행됨" : "발행 전"}
    </StatusTag>
  );
}
