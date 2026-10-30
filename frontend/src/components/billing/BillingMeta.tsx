import {
  countDraftInvoiceLines,
  type DraftInvoiceResponse,
} from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";

export async function BillingMeta({
  draftInvoice,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
}) {
  const result = await draftInvoice;
  if (!result.ok) return null;

  return (
    <>
      고객 <b>{result.data.summary?.customer_count ?? result.data.customers.length}</b>곳, 청구 라인{" "}
      <b>{result.data.summary?.line_count ?? countDraftInvoiceLines(result.data.customers)}</b>줄
    </>
  );
}
