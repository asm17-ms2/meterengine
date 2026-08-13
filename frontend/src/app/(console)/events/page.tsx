import { Suspense } from "react";

import { EventsMeta, EventsSection } from "@/components/events/EventsSection";
import { FilterBar, QueryStamp } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { loadEvents, readPage } from "@/lib/api/events";
import { devStateEnabled, readDevState, type DevState } from "@/lib/dev-state";
import { formatKstStamp } from "@/lib/format";
import { monthOptionsFor, readMonth } from "@/lib/month";

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

  // await하지 않고 넘긴다. 헤더 메타와 표가 같은 응답을 보되 각자의 Suspense
  // 경계에서 기다린다.
  const events =
    devState === "loading" ? null : loadEvents({ month, page }, devState);

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
        <MonthSelect value={month} options={monthOptionsFor(month)} />
        <QueryStamp text={formatKstStamp(new Date())} />
      </FilterBar>

      {events ? (
        <Suspense fallback={<TableSkeleton />}>
          <EventsSection
            events={events}
            month={month}
            hrefFor={(target) => eventsHref(month, target, devState)}
          />
        </Suspense>
      ) : (
        <TableSkeleton />
      )}
    </>
  );
}

/**
 * 이 화면의 주소를 만든다. page는 0부터 세고, 0이면 생략해서 첫 페이지 주소를
 * 깨끗하게 둔다. state는 개발 모드에서만 붙는다 (프로덕션에서는 devStateEnabled가
 * 상수 false라 이 분기가 통째로 제거된다).
 */
function eventsHref(month: string, page: number, devState: DevState): string {
  const params = new URLSearchParams({ month });
  if (page > 0) params.set("page", String(page));
  if (devStateEnabled && devState !== "normal") params.set("state", devState);
  return `/events?${params.toString()}`;
}
