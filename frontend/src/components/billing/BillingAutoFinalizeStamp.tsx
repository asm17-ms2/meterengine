import { QueryStamp } from "@/components/screen/QueryStamp";
import type { DraftInvoiceResponse } from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";
import { formatKstMinuteStamp } from "@/lib/format";

export async function BillingAutoFinalizeStamp({
  draftInvoice,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
}) {
  const result = await draftInvoice;
  if (!result.ok) return null;
  if (result.data.auto_finalize_running) return <QueryStamp label="자동 확정" text="진행 중" />;
  if (result.data.auto_finalize_at === null) return null;

  return (
    <QueryStamp
      label="다음 자동 확정"
      text={formatKstMinuteStamp(new Date(result.data.auto_finalize_at))}
    />
  );
}
