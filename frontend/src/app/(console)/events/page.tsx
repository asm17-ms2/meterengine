import type { Metadata } from "next";
import { CsvDownload } from "@/components/screen/CsvDownload";
import { DataMonths } from "@/components/screen/DataMonths";
import { Suspense } from "react";
import { EventFilters } from "@/components/events/EventFilters";
import { EventsMeta } from "@/components/events/EventsMeta";
import { RoutedEventDetail, WhenEventSelected } from "@/components/events/RoutedEventDetail";
import { formatKstDateTime } from "@/lib/format";
import { summarizeProperties, toRawJson } from "@/lib/api/events";
import { EventsSection } from "@/components/events/EventsSection";
import { ErrorAlert } from "@/components/screen/ErrorAlert";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { ScopedLink } from "@/components/workspace/ScopedLink";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { customerOptions } from "@/lib/api/search";
import type { Result } from "@/lib/api/client";
import { listEvents, listEventTypes, lookupEvent, type ListEventsResponse } from "@/lib/api/events";
import { readListQuery, type MonthlyListQuery } from "@/lib/list-query";

async function Filters({ query }: { query: MonthlyListQuery }) {
  const [customers, types] = await Promise.all([customerOptions(query.customerId), listEventTypes(query)]);
  return <EventFilters key={JSON.stringify(query)} query={query} customers={customers.ok ? customers.data.map(customer => ({ value: customer.value, label: customer.label + (customer.archived ? " (보관됨)" : "") })) : []} types={types.ok ? types.data.types : []} optionsError={!customers.ok || !types.ok ? "고객이나 이벤트 유형 목록을 불러오지 못했어요." : undefined} />;
}

async function SelectedEvent({ transactionId, events }: { transactionId: string; events: Promise<Result<ListEventsResponse>> | null }) {
  // 목록에 있는 이벤트는 표가 받은 값으로 상세를 연다. 목록에 없을 때만 따로 찾는다.
  const list = await events;
  if (list?.ok && list.data.events.some(event => event.transaction_id === transactionId)) return null;
  const result = await lookupEvent(transactionId);
  if (!result.ok) return <WhenEventSelected transactionId={transactionId}><ErrorAlert title="이벤트 상세를 불러오지 못했어요" message={result.error.message} /></WhenEventSelected>;
  const event = result.data;
  return <WhenEventSelected transactionId={transactionId}><RoutedEventDetail row={{ transactionId: event.transaction_id, customerName: event.customer_name, eventType: event.type, occurredAt: formatKstDateTime(event.occurred_at), receivedAt: formatKstDateTime(event.received_at), propertiesPreview: summarizeProperties(event.properties), rawJson: toRawJson(event), requestId: event.request_id, actorType: event.actor_type, actorId: event.actor_id }} /></WhenEventSelected>;
}

export const metadata: Metadata = { title: "수집 이벤트" };

export default async function EventsPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const params = await searchParams;
  const query = readListQuery(params, CAPABILITIES.listEvents, { hasMonth: true });
  const events = query.devState === "loading" ? null : listEvents(query, query.devState);
  const transactionId = typeof params.transaction_id === "string" ? params.transaction_id : undefined;
  return <>
    <ScreenHeader title="수집 이벤트" description={<>받은 사용량 이벤트예요. transaction_id를 누르면 전송 속성이 보여요. 이벤트를 보내는 연동은 <ScopedLink href="/integrations">API 키와 웹훅</ScopedLink>에서 설정하세요.</>}>{events && <Suspense fallback={null}><EventsMeta events={events} /></Suspense>}</ScreenHeader>
    {(query.from || query.to) && <p className="field-hint">사용량과 청구는 달 단위로 집계해요. 사용량이나 청구 검토 화면으로 이동하면 조회 시작 시각이 속한 달을 보여 줘요.</p>}
    {transactionId && <Suspense fallback={<p role="status">이벤트 상세를 불러오는 중이에요.</p>}><SelectedEvent transactionId={transactionId} events={events} /></Suspense>}
    <ScreenCard><Suspense fallback={<p role="status">조회 조건을 불러오는 중이에요.</p>}><Filters query={query} /></Suspense><Suspense fallback={null}><DataMonths resource="events" pathname="/events" query={query} filterForm="event-search" /></Suspense><CsvDownload resource="events" />{events ? <Suspense fallback={<TableSkeleton />}><EventsSection events={events} query={query} /></Suspense> : <TableSkeleton />}</ScreenCard>
  </>;
}
