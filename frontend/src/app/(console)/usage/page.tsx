import { Suspense } from "react";

import { FilterBar } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { QueryStamp } from "@/components/screen/QueryStamp";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { UsageMeta } from "@/components/usage/UsageMeta";
import { UsageSection } from "@/components/usage/UsageSection";
import { aggregateBillableMetricUsages } from "@/lib/api/usage";
import { readDevState } from "@/lib/dev-state";
import { formatKoreanMonth, formatKstStamp } from "@/lib/format";
import { buildMonthOptionsFor, readMonth } from "@/lib/month";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function UsagePage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const devState = readDevState(params.state);

  const usage =
    devState === "loading" ? null : aggregateBillableMetricUsages(month, devState);

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
        <MonthSelect value={month} options={buildMonthOptionsFor(month)} />
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
