import { Suspense } from "react";

import { BillingMeta } from "@/components/billing/BillingMeta";
import { BillingSection } from "@/components/billing/BillingSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { previewDraftInvoice } from "@/lib/api/billing";
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

  // await하지 않고 넘긴다. 헤더 메타와 표가 같은 응답을 보되 각자의 Suspense
  // 경계에서 기다린다.
  const draftInvoice =
    devState === "loading" ? null : previewDraftInvoice(month, devState);

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
        <ExpandControls />
      </FilterBar>

      {draftInvoice ? (
        <Suspense fallback={<TableSkeleton />}>
          <BillingSection draftInvoice={draftInvoice} month={month} />
        </Suspense>
      ) : (
        <TableSkeleton />
      )}
    </CollapseProvider>
  );
}
