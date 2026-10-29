import type { Metadata } from "next";
import { CsvDownload } from "@/components/screen/CsvDownload";
import { DataMonths } from "@/components/screen/DataMonths";
import { Suspense } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { UsageQuality } from "@/components/workspace/UsageQuality";

import { FilterBar } from "@/components/screen/FilterBar";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { QueryStamp } from "@/components/screen/QueryStamp";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { UsageMeta } from "@/components/usage/UsageMeta";
import { UsageSection } from "@/components/usage/UsageSection";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { aggregateBillableMetricUsages } from "@/lib/api/usage";
import { formatKoreanMonth, formatKstStamp } from "@/lib/format";
import { readListQuery } from "@/lib/list-query";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const USAGE_CAPABILITIES = CAPABILITIES.aggregateBillableMetricUsages;

export const metadata: Metadata = { title: "사용량" };

export default async function UsagePage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const query = readListQuery(params, USAGE_CAPABILITIES, { hasMonth: true });
  const { month, devState } = query;

  const usage =
    devState === "loading" ? null : aggregateBillableMetricUsages(query, devState);

  return (
    <CollapseProvider>
      <nav className="workspace-tabs"><Link inheritFilters href="/usage" aria-current="page">사용량</Link><Link inheritFilters href="/events">수집 로그</Link><Link inheritFilters href="/billing">청구 검토로 이동</Link></nav>
      <ScreenHeader title={`사용량 집계 - ${formatKoreanMonth(month)}`}>
        {usage ? (
          <Suspense fallback={null}>
            <UsageMeta usage={usage} />
          </Suspense>
        ) : null}
      </ScreenHeader>
      <Suspense fallback={<p role="status" className="field-hint">수집 상태를 확인하는 중입니다.</p>}><UsageQuality selectedMonth={month} /></Suspense>

      <ScreenCard>
        <FilterBar
          end={
            <>
              <QueryStamp text={formatKstStamp(new Date())} />
              <ExpandControls />
            </>
          }
        >
          <Suspense
            fallback={<span role="status">조회 조건을 불러오는 중입니다.</span>}
          >
            <ListControlsLoader
              resource="usage"
              query={query}
              capabilities={USAGE_CAPABILITIES}
              month={{ value: month }}
            />
          </Suspense>
        </FilterBar>
        <CsvDownload resource="usage" />
        <Suspense fallback={null}><DataMonths filterForm="resource-search-usage" resource="usage" pathname="/usage" query={query} /></Suspense>

        {usage ? (
          <Suspense fallback={<TableSkeleton />}>
            <UsageSection usage={usage} query={query} />
          </Suspense>
        ) : (
          <TableSkeleton />
        )}
      </ScreenCard>
    </CollapseProvider>
  );
}
