import { QueryStamp } from "@/components/screen/QueryStamp";
import type { DraftInvoiceResponse } from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";
import { formatKstStamp } from "@/lib/format";

export async function BillingCalculatedStamp({
  draftInvoice,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
}) {
  const result = await draftInvoice;
  if (!result.ok) return null;

  return <QueryStamp label="계산 시각" text={formatKstStamp(new Date(result.data.calculated_at))} />;
}
