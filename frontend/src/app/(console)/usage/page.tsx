import type { Metadata } from "next";
import { Suspense } from "react";

import { FilterBar } from "@/components/screen/FilterBar";
import { ListControls } from "@/components/screen/ListControls";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { QueryStamp } from "@/components/screen/QueryStamp";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { UsageMeta } from "@/components/usage/UsageMeta";
import { UsageSection } from "@/components/usage/UsageSection";
import { listBillableMetrics } from "@/lib/api/billable-metrics";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { listCustomers } from "@/lib/api/customers";
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
      <ScreenHeader title={`사용량 집계 - ${formatKoreanMonth(month)}`}>
        {usage ? (
          <Suspense fallback={null}>
            <UsageMeta usage={usage} />
          </Suspense>
        ) : null}
      </ScreenHeader>

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
            fallback={
              <ListControls
                query={query}
                capabilities={USAGE_CAPABILITIES}
                month={{ value: month }}
                customers={[]}
                eventTypes={[]}
                isLoadingOptions
              />
            }
          >
            <ListControlsLoader
              query={query}
              capabilities={USAGE_CAPABILITIES}
              month={{ value: month }}
              customers={
                USAGE_CAPABILITIES.customer_id
                  ? listCustomers(readListQuery({}, CAPABILITIES.listCustomers), "normal")
                  : undefined
              }
              metrics={
                USAGE_CAPABILITIES.type
                  ? listBillableMetrics(readListQuery({}, CAPABILITIES.listBillableMetrics), "normal")
                  : undefined
              }
            />
          </Suspense>
        </FilterBar>

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
