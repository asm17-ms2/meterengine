import { EventTable, type EventRowView } from "@/components/events/EventTable";
import { Pagination } from "@/components/events/Pagination";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import {
  summarizeProperties,
  toRawJson,
  totalPages,
  type EventPage,
} from "@/lib/api/events";
import { formatKstDateTime, formatDecimal } from "@/lib/format";
import { shiftMonth } from "@/lib/month";

/**
 * 프라미스를 await하는 서버 컴포넌트. 페이지가 <Suspense>로 감싸서 표 영역만
 * 스켈레톤으로 바뀌고 화면 제목과 필터 행은 남는다.
 */
export async function EventsSection({
  events,
  month,
  hrefFor,
}: {
  events: Promise<Result<EventPage>>;
  month: string;
  /** 0부터 세는 page 번호를 받아 이 화면의 주소를 만든다. */
  hrefFor: (page: number) => string;
}) {
  const result = await events;

  if (!result.ok) {
    return (
      <ErrorState
        title="이벤트를 불러오지 못했습니다"
        error={result.error}
        narrowerHref={`/events?month=${shiftMonth(month, -1)}`}
      />
    );
  }

  const page = result.data;
  const pageCount = totalPages(page);

  if (page.events.length === 0) {
    // 두 가지 경우가 있고 할 말이 다르다. 정말 이 달에 이벤트가 없는 것과,
    // 주소창의 page가 마지막 페이지를 넘어간 것이다. 후자에 '이벤트가 없습니다'라고
    // 하면 방금 본 목록이 사라진 것처럼 읽힌다.
    const outOfRange = page.total > 0;
    return (
      <EmptyState
        title={outOfRange ? "이 페이지에는 이벤트가 없습니다" : "수집된 이벤트가 없습니다"}
        body={
          outOfRange
            ? `${month}의 이벤트는 ${formatDecimal(page.total)}건, ${pageCount}페이지까지입니다. 요청한 페이지가 그 뒤에 있습니다.`
            : `${month}에 수집된 이벤트가 없습니다. 기간을 바꾸거나 이벤트 수집이 동작하는지 확인하세요.`
        }
        resetHref={hrefFor(0)}
      />
    );
  }

  const rows: EventRowView[] = page.events.map((entry) => ({
    transactionId: entry.transaction_id,
    customerName: entry.customer_name,
    eventType: entry.event_type,
    occurredAt: formatKstDateTime(entry.occurred_at),
    receivedAt: formatKstDateTime(entry.received_at),
    propertiesPreview: summarizeProperties(entry.properties),
    rawJson: toRawJson(entry),
  }));

  return (
    <>
      <EventTable rows={rows} />
      <div className="screen-footer">
        <Pagination
          current={page.page + 1}
          total={pageCount}
          hrefFor={(display) => hrefFor(display - 1)}
        />
        {/*
          응답이 정렬 기준을 에코하지 않아서 화면에 고정한다. 백엔드가 정렬을 바꾸면
          이 문구도 같이 고쳐야 한다 (MS2-131 PR에 적어 두었다).
        */}
        <span className="screen-note">정렬: occurred_at 최신순</span>
      </div>
    </>
  );
}

/** 화면 제목 오른쪽 메타. 같은 프라미스를 보되 Suspense 경계가 따로다. */
export async function EventsMeta({
  events,
}: {
  events: Promise<Result<EventPage>>;
}) {
  const result = await events;
  if (!result.ok) return null;

  return (
    <>
      총 <b>{formatDecimal(result.data.total)}</b>건, 이 페이지{" "}
      <b>{result.data.events.length}</b>줄
    </>
  );
}
