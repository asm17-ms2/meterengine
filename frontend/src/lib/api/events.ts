import "server-only";

import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import { toServerParams, type MonthlyListQuery } from "@/lib/list-query";

export type ListEventsResponse = {
  month: string | null;
  from?: string;
  to?: string;
  page: number;
  size: number;
  total: number;
  events: EventResponse[];
};

export type EventResponse = {
  transaction_id: string;
  customer_id: string;
  customer_name: string;
  type: string;
  properties: Record<string, unknown>;
  occurred_at: string;
  received_at: string;
  request_id?: string;
  actor_type?: string;
  actor_id?: string;
};

export function countPages(listEventsResponse: ListEventsResponse): number {
  if (listEventsResponse.total <= 0) return 1;
  return Math.ceil(listEventsResponse.total / Math.max(listEventsResponse.size, 1));
}

export function listEventTypes(query: MonthlyListQuery) {
  const params = toServerParams(query, CAPABILITIES.listEvents);
  delete params.page;
  delete params.size;
  delete params.sort;
  delete params.order;
  delete params.type;
  return serverFetch<{ types: string[] }>(config.apiBaseUrl, "/v1/events/types", params);
}

export function lookupEvent(transactionId: string) {
  return serverFetch<EventResponse>(config.apiBaseUrl, "/v1/events/lookup", { transaction_id: transactionId });
}

export function summarizeProperties(properties: Record<string, unknown>): string {
  const parts = Object.entries(properties).map(([key, value]) => {
    const text = typeof value === "string" ? value : JSON.stringify(value);
    return `${key}=${text}`;
  });
  return parts.length > 0 ? parts.join(", ") : "{}";
}

export function toRawJson(event: EventResponse): string {
  return JSON.stringify(
    {
      transaction_id: event.transaction_id,
      customer_id: event.customer_id,
      type: event.type,
      occurred_at: event.occurred_at,
      received_at: event.received_at,
      properties: event.properties,
      request_id: event.request_id,
      actor_type: event.actor_type,
      actor_id: event.actor_id,
    },
    null,
    2,
  );
}

export async function listEvents(
  query: MonthlyListQuery,
  devState: DevState,
): Promise<Result<ListEventsResponse>> {
  if (devState === "empty") {
    return {
      ok: true,
      data: {
        month: query.month,
        page: query.page,
        size: query.size,
        total: 0,
        events: [],
      },
    };
  }
  if (devState === "error") {
    return {
      ok: false,
      error: {
        status: 503,
        code: "dev_forced",
        message:
          "개발 모드에서 강제한 에러 상태입니다. 사이드바의 표 상태 스위치를 정상으로 되돌리면 사라집니다.",
      },
    };
  }

  return serverFetch<ListEventsResponse>(
    config.apiBaseUrl,
    "/v1/events",
    toServerParams(query, CAPABILITIES.listEvents),
  );
}
