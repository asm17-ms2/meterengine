import { Suspense } from "react";

import { BillingFinalizedLink } from "@/components/billing/BillingFinalizedLink";
import { BillingMeta } from "@/components/billing/BillingMeta";
import { BillingSection } from "@/components/billing/BillingSection";
import { InvoiceFinalizeSection } from "@/components/billing/InvoiceFinalizeSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { previewDraftInvoice } from "@/lib/api/billing";
import { listInvoices } from "@/lib/api/invoices";
import { readDevState } from "@/lib/dev-state";
import { formatKoreanMonth } from "@/lib/format";
import { buildMonthOptionsFor, readMonth } from "@/lib/month";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function BillingPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const devState = readDevState(params.state);

  const draftInvoice =
    devState === "loading" ? null : previewDraftInvoice(month, devState);
  const invoices = listInvoices(month, devState);
  const disabledFinalizeButton = (
    <button type="button" className="btn btn-primary" disabled>
      인보이스 확정
    </button>
  );

  return (
    <CollapseProvider>
      <ScreenHeader
        title={
          <>
            청구 예정액 - {formatKoreanMonth(month)}{" "}
            <span className="tag tag-outline">확정 전 (draft)</span>
          </>
        }
      >
        {draftInvoice ? (
          <Suspense fallback={null}>
            <BillingMeta draftInvoice={draftInvoice} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <FilterBar>
        <MonthSelect value={month} options={buildMonthOptionsFor(month)} />
        {draftInvoice ? (
          <>
            <Suspense fallback={disabledFinalizeButton}>
              <InvoiceFinalizeSection draftInvoice={draftInvoice} month={month} />
            </Suspense>
            <Suspense fallback={null}>
              <BillingFinalizedLink invoices={invoices} month={month} />
            </Suspense>
          </>
        ) : (
          disabledFinalizeButton
        )}
        <ExpandControls />
      </FilterBar>

      {draftInvoice ? (
        <Suspense fallback={<TableSkeleton />}>
          <BillingSection draftInvoice={draftInvoice} invoices={invoices} month={month} />
        </Suspense>
      ) : (
        <TableSkeleton />
      )}
    </CollapseProvider>
  );
}
