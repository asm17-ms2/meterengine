import { LinkButton } from "@/components/screen/LinkButton";
import type { Result } from "@/lib/api/client";
import type { ListInvoicesResponse } from "@/lib/api/invoices";

export async function BillingFinalizedLink({
  invoices,
  month,
}: {
  invoices: Promise<Result<ListInvoicesResponse>>;
  month: string;
}) {
  const result = await invoices;
  if (!result.ok || result.data.invoices.length === 0) return null;

  return (
    <LinkButton type="link" href={`/invoices?month=${month}`}>
      이 달에 확정된 인보이스 {result.data.invoices.length}건 보기
    </LinkButton>
  );
}
