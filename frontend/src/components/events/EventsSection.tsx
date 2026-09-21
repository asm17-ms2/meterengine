import { EventsTable, type EventRowView } from "@/components/events/EventsTable";
import { Pagination } from "@/components/events/Pagination";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import {
  summarizeProperties,
  toRawJson,
  countPages,
  type ListEventsResponse,
} from "@/lib/api/events";
import { formatKstDateTime, formatDecimal } from "@/lib/format";
import { shiftMonth } from "@/lib/month";

export async function EventsSection({
  events,
  month,
  buildHref,
}: {
  events: Promise<Result<ListEventsResponse>>;
  month: string;
  /** 0부터 세는 page 번호를 받아 이 화면의 주소를 만든다. */
  buildHref: (page: number) => string;
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

  const listEventsResponse = result.data;
  const pageCount = countPages(listEventsResponse);

  if (listEventsResponse.events.length === 0) {
    const isOutOfRange = listEventsResponse.total > 0;
    return (
      <EmptyState
        title={isOutOfRange ? "이 페이지에는 이벤트가 없습니다" : "수집된 이벤트가 없습니다"}
        body={
          isOutOfRange
            ? `${month}의 이벤트는 ${formatDecimal(listEventsResponse.total)}건, ${pageCount}페이지까지입니다. 요청한 페이지가 그 뒤에 있습니다.`
            : `${month}에 수집된 이벤트가 없습니다. 기간을 바꾸거나 이벤트 수집이 동작하는지 확인하세요.`
        }
        resetHref={buildHref(0)}
      />
    );
  }

  const rows: EventRowView[] = listEventsResponse.events.map((event) => ({
    transactionId: event.transaction_id,
    customerName: event.customer_name,
    eventType: event.type,
    occurredAt: formatKstDateTime(event.occurred_at),
    receivedAt: formatKstDateTime(event.received_at),
    propertiesPreview: summarizeProperties(event.properties),
    rawJson: toRawJson(event),
  }));

  return (
    <>
      <EventsTable rows={rows} />
      <div className="screen-footer">
        <Pagination
          currentPage={listEventsResponse.page + 1}
          pageCount={pageCount}
          buildHref={(displayPage) => buildHref(displayPage - 1)}
        />
        <span className="screen-note">정렬: occurred_at 최신순</span>
      </div>
    </>
  );
}
