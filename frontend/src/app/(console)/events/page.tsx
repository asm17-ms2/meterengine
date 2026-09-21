import { Suspense } from "react";

import { EventsMeta } from "@/components/events/EventsMeta";
import { EventsSection } from "@/components/events/EventsSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { QueryStamp } from "@/components/screen/QueryStamp";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { listEvents, readPage } from "@/lib/api/events";
import { isDevStateEnabled, readDevState, type DevState } from "@/lib/dev-state";
import { formatKstStamp } from "@/lib/format";
import { buildMonthOptionsFor, readMonth } from "@/lib/month";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function EventsPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const page = readPage(params.page);
  const devState = readDevState(params.state);

  const events =
    devState === "loading" ? null : listEvents({ month, page }, devState);

  return (
    <>
      <ScreenHeader title="이벤트 로그">
        {events ? (
          <Suspense fallback={null}>
            <EventsMeta events={events} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <FilterBar>
        <MonthSelect value={month} options={buildMonthOptionsFor(month)} />
        <QueryStamp text={formatKstStamp(new Date())} />
      </FilterBar>

      {events ? (
        <Suspense fallback={<TableSkeleton />}>
          <EventsSection
            events={events}
            month={month}
            buildHref={(targetPage) => buildEventsHref(month, targetPage, devState)}
          />
        </Suspense>
      ) : (
        <TableSkeleton />
      )}
    </>
  );
}

function buildEventsHref(month: string, page: number, devState: DevState): string {
  const params = new URLSearchParams({ month });
  if (page > 0) params.set("page", String(page));
  if (isDevStateEnabled && devState !== "normal") params.set("state", devState);
  return `/events?${params.toString()}`;
}
