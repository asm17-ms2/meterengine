import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

/** GET /v1/events 응답. 백엔드의 ListEventsResponse(MS2-131)와 1:1이다. */
export type ListEventsResponse = {
  /** 서버가 계산한 조회 월. 요청이 month를 생략했을 때 어느 달로 갔는지 알려준다. */
  month: string;
  /** 0부터 센다. 화면의 1페이지가 page=0이다. */
  page: number;
  size: number;
  /** 필터를 적용한 뒤의 전체 건수. 이 페이지의 개수가 아니다. */
  total: number;
  events: EventResponse[];
};

export type EventResponse = {
  transaction_id: string;
  customer_id: string;
  customer_name: string;
  type: string;
  /**
   * 백엔드는 jsonb 문자열을 @JsonRawValue로 그대로 박아 넣는다. 파싱과 재직렬화를
   * 한 번도 거치지 않으려는 것인데, 그 보호는 여기서 끝난다. 우리 쪽 response.json()이
   * 결국 JSON.parse를 태우므로 숫자는 float64가 된다. 2^53을 넘는 정수나 자릿수가 긴
   * 소수는 표시가 어긋날 수 있다. 이 화면은 값을 보여주기만 하고 계산에 쓰지 않아서
   * 지금은 문제가 없다.
   */
  properties: Record<string, unknown>;
  /**
   * ISO 8601. 보낸 값의 오프셋과 무관하게 백엔드가 UTC(`Z`)로 정규화해서 내려준다.
   * 표시는 formatKstDateTime이 KST로 바꿔 그리고, 상세 드로어의 원본 JSON에는 `Z`가 그대로 나온다.
   */
  occurred_at: string;
  received_at: string;
};

export type EventQuery = {
  month: string;
  /** 0부터 세는 페이지 번호. */
  page: number;
};

/**
 * 한 페이지에 담을 개수.
 *
 * 백엔드 기본값도 20이지만 명시적으로 보낸다. 화면의 행 수는 화면이 정할 일이라,
 * 서버 기본값이 바뀌었을 때 표가 조용히 길어지거나 짧아지면 안 된다.
 * 상한은 백엔드가 100으로 막는다.
 */
export const PAGE_SIZE = 20;

/**
 * 쿼리스트링의 page를 읽는다. 0부터 세는 값이고, 숫자가 아니거나 음수면 0으로 떨어뜨린다.
 * readMonth와 같은 방침이다. 조회 전용 화면이라 400을 띄우는 것보다 첫 페이지를
 * 보여주는 편이 낫다.
 */
export function readPage(raw: string | string[] | undefined): number {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const parsed = Number(value);
  if (value === undefined || value === "" || !Number.isInteger(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

/** 응답의 total과 size로 마지막 페이지 번호를 만든다. 백엔드는 total만 준다. */
export function totalPages(page: ListEventsResponse): number {
  if (page.total <= 0) return 1;
  return Math.ceil(page.total / Math.max(page.size, 1));
}

/**
 * 표의 properties 칸에 한 줄로 넣을 요약.
 *
 * 디자인은 `model=gpt-4o, token=2040` 형태로 키와 값을 늘어놓는다. 중괄호와 따옴표를
 * 빼서 좁은 칸에 더 많은 키가 보인다. 전체 원본은 상세 드로어의 JSON이 보여준다.
 *
 * 숫자에 천 단위 구분을 넣지 않는 이유: 이 칸은 저장된 값을 그대로 미리 보는 자리다.
 * 여기서만 2,040으로 보이고 드로어의 JSON에는 2040으로 나오면 다른 값처럼 읽힌다.
 */
export function summarizeProperties(properties: Record<string, unknown>): string {
  const parts = Object.entries(properties).map(([key, value]) => {
    const text = typeof value === "string" ? value : JSON.stringify(value);
    return `${key}=${text}`;
  });
  return parts.length > 0 ? parts.join(", ") : "{}";
}

/**
 * 상세 드로어의 <pre>에 넣을 원본 JSON.
 *
 * customer_name은 뺀다. 그건 customer 테이블을 조인해 붙여 준 표시용 값이라
 * 저장된 이벤트에는 없다. 드로어 위쪽 정의 목록이 이미 고객 이름을 보여주고 있다.
 */
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

/**
 * 개발 모드 상태 스위치는 네트워크 호출 전에 갈린다 (loadUsage와 같은 구조).
 * 'loading'은 여기 오지 않는다. 페이지가 로더를 부르지 않고 스켈레톤으로 단락한다.
 */
export async function loadEvents(
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
        title: "개발 모드에서 강제한 에러 상태입니다",
        detail: "사이드바의 표 상태 스위치를 정상으로 되돌리면 사라집니다.",
      },
    };
  }

  return serverFetch<ListEventsResponse>(config.apiBaseUrl, "/v1/events", {
    month: query.month,
    page: String(query.page),
    size: String(PAGE_SIZE),
  });
}
