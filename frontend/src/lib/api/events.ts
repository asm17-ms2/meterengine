import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type ListEventsResponse = {
  month: string;
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
};

export type EventQuery = {
  month: string;
  /** 0부터 세는 페이지 번호. */
  page: number;
};

export const PAGE_SIZE = 20;

export function readPage(raw: string | string[] | undefined): number {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const parsed = Number(value);
  if (value === undefined || value === "" || !Number.isInteger(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

export function countPages(listEventsResponse: ListEventsResponse): number {
  if (listEventsResponse.total <= 0) return 1;
  return Math.ceil(listEventsResponse.total / Math.max(listEventsResponse.size, 1));
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
    },
    null,
    2,
  );
}

export async function listEvents(
  query: EventQuery,
  devState: DevState,
): Promise<Result<ListEventsResponse>> {
  if (devState === "empty") {
    return {
      ok: true,
      data: {
        month: query.month,
        page: query.page,
        size: PAGE_SIZE,
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

  return serverFetch<ListEventsResponse>(config.apiBaseUrl, "/v1/events", {
    month: query.month,
    page: String(query.page),
    size: String(PAGE_SIZE),
  });
}
