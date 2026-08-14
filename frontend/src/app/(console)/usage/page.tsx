import { Suspense } from "react";

import { FilterBar, QueryStamp } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import {
  CollapseProvider,
  ExpandControls,
} from "@/components/table/CollapseProvider";
import { UsageMeta, UsageSection } from "@/components/usage/UsageSection";
import { loadUsage } from "@/lib/api/usage";
import { readDevState } from "@/lib/dev-state";
import { formatKoreanMonth, formatKstStamp } from "@/lib/format";
import { monthOptionsFor, readMonth } from "@/lib/month";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function UsagePage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const devState = readDevState(params.state);

  // await하지 않고 넘긴다. 헤더 메타와 표가 같은 응답을 보되 각자의 Suspense
  // 경계에서 기다린다.
  const usage =
    devState === "loading" ? null : loadUsage(month, devState);

  return (
    <CollapseProvider>
      <ScreenHeader title={`사용량 집계 - ${formatKoreanMonth(month)}`}>
        {usage ? (
          <Suspense fallback={null}>
            <UsageMeta usage={usage} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <FilterBar>
        <MonthSelect value={month} options={monthOptionsFor(month)} />
        <QueryStamp text={formatKstStamp(new Date())} />
        <ExpandControls />
      </FilterBar>

      {usage ? (
        <Suspense fallback={<TableSkeleton />}>
          <UsageSection usage={usage} month={month} />
        </Suspense>
      ) : (
        <TableSkeleton />
      )}
    </CollapseProvider>
  );
}
