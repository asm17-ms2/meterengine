import {
  countDraftInvoiceLines,
  type DraftInvoiceResponse,
} from "@/lib/api/billing";
import type { Result } from "@/lib/api/client";
import { formatKstStamp } from "@/lib/format";

/** 화면 제목 오른쪽 메타. 같은 프라미스를 보되 Suspense 경계가 따로다. */
export async function BillingMeta({
  draftInvoice,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
}) {
  const result = await draftInvoice;
  if (!result.ok) return null;

  return (
    <>
      고객 <b>{result.data.customers.length}</b>곳, 청구 라인{" "}
      <b>{countDraftInvoiceLines(result.data.customers)}</b>줄
      <br />
      계산 시각 {formatKstStamp(new Date(result.data.calculated_at))}
    </>
  );
}
