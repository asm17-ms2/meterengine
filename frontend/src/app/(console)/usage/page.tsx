import type { Metadata } from "next";
import { CsvDownload } from "@/components/screen/CsvDownload";
import { DataMonths } from "@/components/screen/DataMonths";
import { Suspense } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";

import { CutoffStamp } from "@/components/screen/CutoffStamp";
import { FilterBar } from "@/components/screen/FilterBar";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { QueryStamp } from "@/components/screen/QueryStamp";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { AggregateLoading } from "@/components/screen/AggregateLoading";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { UsageMeta } from "@/components/usage/UsageMeta";
import { UsageSection } from "@/components/usage/UsageSection";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { aggregateBillableMetricUsages } from "@/lib/api/usage";
import { formatKoreanMonth, formatKstStamp } from "@/lib/format";
import { readListQuery } from "@/lib/list-query";
import { searchKey } from "@/lib/search-key";

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
  const loadingKey = searchKey(params);
  const now = new Date();

  const usage =
    devState === "loading" ? null : aggregateBillableMetricUsages(query, devState);

  return (
    <CollapseProvider>
      <ScreenHeader title={`사용량 집계 - ${formatKoreanMonth(month)}`}>
        {usage ? (
          <Suspense key={loadingKey} fallback={null}>
            <UsageMeta usage={usage} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <ScreenCard>
        <FilterBar
          end={
            <>
              <CutoffStamp month={month} now={now} />
              <QueryStamp text={formatKstStamp(now)} />
              <ExpandControls />
            </>
          }
        >
          <Suspense
            fallback={<span role="status">조회 조건을 불러오는 중이에요.</span>}
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
          <Suspense key={loadingKey} fallback={<AggregateLoading title="사용량을 집계하는 중이에요" />}>
            <UsageSection usage={usage} query={query} />
          </Suspense>
        ) : (
          <AggregateLoading title="사용량을 집계하는 중이에요" />
        )}
      </ScreenCard>
      <p className="field-hint">이 달의 청구 금액은 <Link inheritFilters href="/billing">청구 검토</Link>에서 확인하세요.</p>
    </CollapseProvider>
  );
}
