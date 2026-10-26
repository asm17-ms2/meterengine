import { Suspense } from "react";

import { EventsMeta } from "@/components/events/EventsMeta";
import { EventsSection } from "@/components/events/EventsSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { ListControls } from "@/components/screen/ListControls";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { QueryStamp } from "@/components/screen/QueryStamp";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { listBillableMetrics } from "@/lib/api/billable-metrics";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { listCustomers } from "@/lib/api/customers";
import { listEvents } from "@/lib/api/events";
import { formatKstStamp } from "@/lib/format";
import { readListQuery } from "@/lib/list-query";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function EventsPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const capabilities = CAPABILITIES.listEvents;
  const query = readListQuery(params, capabilities, { hasMonth: true });
  const { month, devState } = query;

  const events = devState === "loading" ? null : listEvents(query, devState);

  return (
    <>
      <ScreenHeader title="이벤트 로그">
        {events ? (
          <Suspense fallback={null}>
            <EventsMeta events={events} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <ScreenCard>
        <FilterBar end={<QueryStamp text={formatKstStamp(new Date())} />}>
          <Suspense
            fallback={
              <ListControls
                query={query}
                capabilities={capabilities}
                month={{ value: month }}
                customers={[]}
                eventTypes={[]}
                isLoadingOptions
              />
            }
          >
            <ListControlsLoader
              query={query}
              capabilities={capabilities}
              month={{ value: month }}
              customers={
                capabilities.customer_id
                  ? listCustomers(readListQuery({}, CAPABILITIES.listCustomers), "normal")
                  : undefined
              }
              metrics={
                capabilities.type
                  ? listBillableMetrics(readListQuery({}, CAPABILITIES.listBillableMetrics), "normal")
                  : undefined
              }
            />
          </Suspense>
        </FilterBar>

        {events ? (
          <Suspense fallback={<TableSkeleton />}>
            <EventsSection events={events} query={query} />
          </Suspense>
        ) : (
          <TableSkeleton />
        )}
      </ScreenCard>
    </>
  );
}
