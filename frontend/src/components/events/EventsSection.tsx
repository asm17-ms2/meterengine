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
import { formatKstDateTime, formatDecimal, formatKoreanMonth } from "@/lib/format";
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
  const periodLabel = query.from || query.to ? "선택한 기간" : formatKoreanMonth(month);
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
        title="이벤트를 불러오지 못했어요"
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
        title={isOutOfRange ? "이 페이지에는 이벤트가 없어요" : "조건에 맞는 이벤트가 없어요"}
        body={
          isOutOfRange
            ? `${periodLabel} 이벤트는 ${formatDecimal(listEventsResponse.total)}건이고 ${pageCount}페이지까지 있어요. 요청한 페이지는 그 뒤예요.`
            : `${periodLabel}에 조건과 일치하는 이벤트가 없어요. 기간이나 검색어를 바꿔 보고, 그래도 없으면 이벤트를 보내는 서버 연동을 확인하세요.`
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
