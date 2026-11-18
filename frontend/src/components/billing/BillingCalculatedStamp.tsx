import { QueryStamp } from "@/components/screen/QueryStamp";
import type { DraftInvoiceResponse } from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";
import { formatKstShortStamp } from "@/lib/format";

export async function BillingCalculatedStamp({
  draftInvoice,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
}) {
  const result = await draftInvoice;
  if (!result.ok) return null;

  return <QueryStamp label="계산 시각" text={formatKstShortStamp(new Date(result.data.calculated_at))} />;
}
