import { EventsTable, type EventRowView } from "@/components/events/EventsTable";
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
import { buildListHref, type MonthlyListQuery } from "@/lib/list-query";
import { shiftMonth } from "@/lib/month";

export async function EventsSection({
  events,
  query,
}: {
  events: Promise<Result<ListEventsResponse>>;
  query: MonthlyListQuery;
}) {
  const result = await events;
  const { month } = query;
  const periodLabel = query.from || query.to ? "선택한 기간" : month;
  const resetHref = buildListHref("/events", query, {
    customerId: undefined,
    type: undefined,
    q: undefined,
    properties: undefined,
    size: undefined,
    page: undefined,
  });

  if (!result.ok) {
    return (
      <ErrorState
        title="이벤트를 불러오지 못했습니다"
        error={result.error}
        narrowerHref={`/events?month=${shiftMonth(month, -1)}`}
        resetHref={query.customerId || query.type || query.q || query.properties ? resetHref : undefined}
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
            ? `${periodLabel}의 이벤트는 ${formatDecimal(listEventsResponse.total)}건, ${pageCount}페이지까지입니다. 요청한 페이지가 그 뒤에 있습니다.`
            : `${periodLabel}에 조건과 일치하는 이벤트가 없습니다. 기간이나 검색어를 바꾸고 이벤트 수집이 동작하는지 확인하세요.`
        }
        resetHref={isOutOfRange ? buildListHref("/events", query, { page: 0 }) : resetHref}
        resetLabel={isOutOfRange ? "첫 페이지 보기" : "조건 초기화"}
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
    requestId: event.request_id,
    actorType: event.actor_type,
    actorId: event.actor_id,
  }));

  return (
    <EventsTable
      rows={rows}
      query={query}
      total={listEventsResponse.total}
      note={`정렬: ${{ occurred_at: "발생 시각", received_at: "수신 시각", transaction_id: "전송 ID", customer_name: "고객명", type: "이벤트 유형" }[query.sort ?? "occurred_at"] ?? "발생 시각"} ${query.order === "asc" ? "오름차순" : "내림차순"}`}
    />
  );
}
