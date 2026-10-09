import { InvoicesFrame } from "@/components/invoices/InvoicesFrame";
import { InvoicesScreen } from "@/components/invoices/InvoicesScreen";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import type { ListInvoicesResponse } from "@/lib/api/invoices";
import { formatKstMonth, shiftMonth } from "@/lib/month";

export async function InvoicesSection({
  invoices,
  month,
}: {
  invoices: Promise<Result<ListInvoicesResponse>>;
  month: string | undefined;
}) {
  const result = await invoices;

  if (!result.ok) {
    return (
      <InvoicesFrame month={month}>
        <ErrorState
          title="인보이스 목록을 불러오지 못했습니다"
          error={result.error}
          narrowerHref={month ? undefined : `/invoices?month=${shiftMonth(formatKstMonth(), -1)}`}
        />
      </InvoicesFrame>
    );
  }

  return <InvoicesScreen invoices={result.data.invoices} month={month} />;
}
