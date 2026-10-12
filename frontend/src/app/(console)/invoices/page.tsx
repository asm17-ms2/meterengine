import { Suspense } from "react";

import { InvoicesSection } from "@/components/invoices/InvoicesSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { listInvoices } from "@/lib/api/invoices";
import { readDevState } from "@/lib/dev-state";
import { formatKoreanMonth } from "@/lib/format";
import { buildMonthOptionsFor, readMonth } from "@/lib/month";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function InvoicesPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const devState = readDevState(params.state);

  const invoices = devState === "loading" ? null : listInvoices(month, devState);

  return (
    <CollapseProvider>
      <ScreenHeader
        title={
          <>
            인보이스 - {formatKoreanMonth(month)}{" "}
            <span className="tag tag-outline">확정 (finalized)</span>
          </>
        }
      />

      <FilterBar>
        <MonthSelect value={month} options={buildMonthOptionsFor(month)} />
        <ExpandControls />
      </FilterBar>

      {invoices ? (
        <Suspense fallback={<TableSkeleton />}>
          <InvoicesSection invoices={invoices} month={month} />
        </Suspense>
      ) : (
        <TableSkeleton />
      )}
    </CollapseProvider>
  );
}
