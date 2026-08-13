import type { EventEntry, EventPage, EventQuery } from "@/lib/api/events";
import { PAGE_SIZE } from "@/lib/api/events";

/**
 * GET /v1/events(MS2-131, PR #24)가 머지되기 전까지 쓰는 목 생성기.
 *
 * 순수 함수이고 결정적이다. 같은 month를 주면 항상 같은 이벤트가 나온다. 렌더링마다
 * 값이 흔들리면 페이지를 넘길 때 행이 뒤섞여 페이지네이션을 검증할 수 없다. 그래서
 * Math.random도 Date.now도 쓰지 않고, month에서 뽑은 씨앗으로 난수를 만든다.
 *
 * 실제 응답과 다른 점이 하나 있다. 시드(R__seed.sql)는 '베타 스튜디오'에 이벤트를
 * 일부러 두지 않지만(사용량 0을 확인하려고), 목은 양쪽 고객에 모두 만든다. 고객 열이
 * 실제로 갈리는 것을 화면에서 봐야 해서다. 실제 백엔드로 전환하면 POST를 보낸 고객의
 * 이벤트만 보인다.
 *
 * event_type이 한 종류인 것은 의도다. 시드의 미터가 chat_completion 하나만 쓰고,
 * 목이 없는 종류를 지어내면 전환한 순간 열이 통째로 달라진다.
 */

const SEEDED_CUSTOMERS = [
  { id: "a728e7b6-d82b-4f3c-a960-a66a02794c1d", name: "아크메 주식회사" },
  { id: "252339bc-d5f8-472d-b5d6-ed8554049450", name: "베타 스튜디오" },
] as const;

const EVENT_TYPE = "chat_completion";
const MODELS = ["gpt-4o", "gpt-4o-mini"] as const;

/** 페이지네이션이 축약(...)까지 그려지도록 여러 페이지가 나오는 건수로 잡았다. */
const EVENT_COUNT = 137;

/** 어느 달이든 존재하는 날짜만 쓴다. 2월 30일 같은 값을 피하려는 것이다. */
const MAX_DAY = 28;

/** mulberry32. 짧고 상태가 32비트 하나뿐이라 목 데이터에 충분하다. */
function mulberry32(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function seedOf(month: string): number {
  let hash = 2166136261;
  for (let i = 0; i < month.length; i++) {
    hash ^= month.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

/**
 * 백엔드가 실제로 내려주는 시각 형식.
 *
 * 월 경계는 KST로 판정하지만 직렬화는 UTC로 정규화된다. +09:00으로 보낸 이벤트도
 * 조회하면 Z로 돌아온다 (PR #24 브랜치를 띄워 실측: 14:12:04+09:00 -> 05:12:04Z).
 * 화면의 formatKstDateTime이 어느 쪽이든 KST로 바꿔 그리므로 표시에는 차이가 없지만,
 * 상세 드로어의 원본 JSON은 받은 문자열을 그대로 보여주므로 목도 같은 형식이어야 한다.
 *
 * fraction은 소수점 이하 자릿수다. occurred_at은 클라이언트가 보낸 값이라 초 단위로
 * 떨어지고, received_at은 DB의 now()가 찍어서 마이크로초까지 붙는다.
 */
function toUtcIso(utcMs: number, fractionDigits: 0 | 6): string {
  const at = new Date(utcMs);
  const pad = (value: number) => String(value).padStart(2, "0");
  const base =
    `${at.getUTCFullYear()}-${pad(at.getUTCMonth() + 1)}-${pad(at.getUTCDate())}` +
    `T${pad(at.getUTCHours())}:${pad(at.getUTCMinutes())}:${pad(at.getUTCSeconds())}`;
  if (fractionDigits === 0) return `${base}Z`;
  const micros = String(at.getUTCMilliseconds() * 1000).padStart(6, "0");
  return `${base}.${micros}Z`;
}

function buildEvents(month: string): EventEntry[] {
  const random = mulberry32(seedOf(month));
  const [year, monthNumber] = month.split("-").map(Number);
  const entries: EventEntry[] = [];

  for (let i = 0; i < EVENT_COUNT; i++) {
    const day = 1 + Math.floor(random() * MAX_DAY);
    const hour = Math.floor(random() * 24);
    const minute = Math.floor(random() * 60);
    const second = Math.floor(random() * 60);

    // KST 벽시계 시각을 UTC 순간으로 바꾼다 (KST = UTC+9라 9시간을 뺀다).
    const occurredMs = Date.UTC(year, monthNumber - 1, day, hour - 9, minute, second);
    // 수집까지 걸린 시간. received_at은 항상 occurred_at 뒤다.
    const receivedMs = occurredMs + (1 + Math.floor(random() * 90)) * 1000 + 123;

    const customer = SEEDED_CUSTOMERS[Math.floor(random() * SEEDED_CUSTOMERS.length)];
    const model = MODELS[Math.floor(random() * MODELS.length)];
    const token = 100 + Math.floor(random() * 3900);

    entries.push({
      transaction_id: `mock-${month}-${String(i + 1).padStart(4, "0")}`,
      customer_id: customer.id,
      customer_name: customer.name,
      event_type: EVENT_TYPE,
      properties: { model, token, region: "kr-1" },
      occurred_at: toUtcIso(occurredMs, 0),
      received_at: toUtcIso(receivedMs, 6),
    });
  }

  // 백엔드와 같은 정렬이다. occurred_at만으로는 같은 초에 몰린 이벤트의 순서가
  // 정해지지 않아 페이지 경계에서 행이 중복되거나 빠진다. transaction_id로 마무리한다.
  entries.sort((a, b) => {
    if (a.occurred_at !== b.occurred_at) {
      return a.occurred_at < b.occurred_at ? 1 : -1;
    }
    return a.transaction_id < b.transaction_id ? 1 : -1;
  });
  return entries;
}

/**
 * 한 페이지를 잘라 낸다. 범위를 벗어난 page는 빈 배열이다 (백엔드와 같다).
 *
 * size를 받는 이유는 목 Route Handler 때문이다. 화면은 항상 PAGE_SIZE로 부르지만,
 * 계약 시연용 curl은 size를 바꿔 가며 볼 수 있어야 한다.
 */
export function mockEventPage(query: EventQuery, size: number = PAGE_SIZE): EventPage {
  const all = buildEvents(query.month);
  const start = query.page * size;
  return {
    month: query.month,
    page: query.page,
    size,
    total: all.length,
    events: all.slice(start, start + size),
  };
}
