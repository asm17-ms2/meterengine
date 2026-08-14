import { Suspense } from "react";

import {
  BillingMeta,
  BillingSection,
} from "@/components/billing/BillingSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import {
  CollapseProvider,
  ExpandControls,
} from "@/components/table/CollapseProvider";
import { loadDraftInvoice } from "@/lib/api/invoice";
import { readDevState } from "@/lib/dev-state";
import { formatKoreanMonth } from "@/lib/format";
import { monthOptionsFor, readMonth } from "@/lib/month";

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
  const invoice =
    devState === "loading" ? null : loadDraftInvoice(month, devState);

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
        {invoice ? (
          <Suspense fallback={null}>
            <BillingMeta invoice={invoice} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <FilterBar>
        <MonthSelect value={month} options={monthOptionsFor(month)} />
        <ExpandControls />
      </FilterBar>

      {invoice ? (
        <Suspense fallback={<TableSkeleton />}>
          <BillingSection invoice={invoice} month={month} />
        </Suspense>
      ) : (
        <TableSkeleton />
      )}
    </CollapseProvider>
  );
}
