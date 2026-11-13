import { EVENT_TYPE, METRICS, MODELS, TRANSACTION_ID_PREFIX, type SurfaceId } from "../../src/lib/catalog.ts";
import type { DemoCustomer, UsagePattern } from "./customers.ts";
import type { UsageRow } from "./prices.ts";
import { createRandom, digest, type Random } from "./random.ts";

export type TokenProperties = {
  model: string;
  surface: SurfaceId;
  input_tokens: number;
  output_tokens: number;
  cache_read_tokens: number;
  cache_write_tokens: number;
  latency_ms: number;
};
export type DemoEvent = { customerSlug: string; transactionId: string; type: string; timestamp: string; properties: TokenProperties };

const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;
const KST_OFFSET_MS = 9 * HOUR_MS;

type Weighted<T> = ReadonlyArray<readonly [T, number]>;
const DAILY_REQUESTS = { large: [450, 1700], medium: [35, 120], small: [5, 25], new: [0, 10] } as const;
// 작은 곳과 신규 고객은 고객마다 정해진 모델 한두 개만 쓴다.
const COMPACT_MODEL_SETS: Weighted<readonly string[]> = [[["ab-5"], 1], [["ab-5-mini"], 1], [["ab-5", "ab-5-mini"], 1]];
const WEEKEND_RATIO: Record<UsagePattern, readonly [number, number]> = { api: [0.3, 0.45], internal: [0.05, 0.15], data: [0.5, 0.7] };
const SURFACE_MIX: Record<UsagePattern, Weighted<SurfaceId>> = {
  api: [["api", 0.85], ["batch", 0.15]],
  internal: [["chat", 0.7], ["api", 0.3]],
  data: [["batch", 0.6], ["api", 0.4]],
};
const MODEL_MIX: Record<UsagePattern, Partial<Record<SurfaceId, Weighted<string>>>> = {
  api: {
    api: [["ab-5", 0.35], ["ab-5-mini", 0.4], ["ab-5-nano", 0.15], ["ab-embed-3", 0.1]],
    batch: [["ab-5", 0.3], ["ab-5-mini", 0.4], ["ab-5-nano", 0.2], ["ab-embed-3", 0.1]],
  },
  internal: {
    chat: [["ab-5", 0.45], ["ab-5-mini", 0.55]],
    api: [["ab-5", 0.4], ["ab-5-mini", 0.4], ["ab-5-nano", 0.1], ["ab-embed-3", 0.1]],
  },
  data: {
    batch: [["ab-embed-3", 0.45], ["ab-5-nano", 0.25], ["ab-5-mini", 0.2], ["ab-5", 0.1]],
    api: [["ab-embed-3", 0.25], ["ab-5-mini", 0.3], ["ab-5-nano", 0.2], ["ab-5", 0.25]],
  },
};
// 한국 시간 0시부터 23시까지의 상대 비중.
const HOURS = {
  service: [2, 1.2, 0.8, 0.5, 0.5, 0.6, 1, 1.8, 3, 4.5, 5.5, 6, 5.5, 6, 6.5, 6.5, 6, 5.5, 5, 5.5, 6, 6, 5, 3.5],
  office: [0.1, 0.05, 0.05, 0.05, 0.05, 0.1, 0.3, 0.8, 2.5, 7, 9, 9, 4, 8, 9.5, 9.5, 9, 7, 3, 1.5, 1, 0.8, 0.4, 0.2],
  night: [0.3, 4, 5, 5, 4, 2, 0.3, 0.1, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.1],
} as const;
// 글 모델의 토큰 분포. catalog의 글 모델은 모두 여기 있어야 한다.
export const TEXT_MODELS: Record<string, { context: number; baseLatency: number; perToken: number; outputScale: number }> = {
  "ab-5": { context: 150_000, baseLatency: 600, perToken: 22, outputScale: 1 },
  "ab-5-mini": { context: 130_000, baseLatency: 300, perToken: 9, outputScale: 1 },
  "ab-5-nano": { context: 100_000, baseLatency: 150, perToken: 4, outputScale: 0.5 },
};
const EMBEDDING_INPUT = { api: 2000, batch: 6000 } as const;
// 토큰 값의 상한. 넘으면 다시 뽑아서 상한에 값이 쌓이지 않는다.
export const TOKEN_LIMITS = { input_tokens: 400_000, output_tokens: 32_000, cache_read_tokens: 1_000_000, cache_write_tokens: 400_000, embedding_input_tokens: 300_000 } as const;
const SURFACE_TOKENS: Record<SurfaceId, { output: number; input: number; cacheHit: number }> = {
  chat: { output: 520, input: 900, cacheHit: 0.92 },
  api: { output: 450, input: 2200, cacheHit: 0.7 },
  batch: { output: 380, input: 3000, cacheHit: 0.5 },
};

function bounded(random: Random, median: number, sigma: number, limit: number): number {
  for (let attempt = 0; attempt < 16; attempt++) {
    const value = Math.round(random.logNormal(median, sigma));
    if (value >= 1 && value < limit) return value;
  }
  return Math.round(median);
}
const hourWeights = (hours: readonly number[]): Weighted<number> => hours.map((weight, hour) => [hour, weight] as const);

export function kstDate(instant: Date): string {
  return new Date(instant.getTime() + KST_OFFSET_MS).toISOString().slice(0, 10);
}

export function kstMonth(instant: Date): string {
  return kstDate(instant).slice(0, 7);
}

function kstMidnight(date: string): number {
  const [year, month, day] = date.split("-").map(Number);
  return Date.UTC(year, month - 1, day) - KST_OFFSET_MS;
}

function isWeekend(date: string): boolean {
  const weekday = new Date(kstMidnight(date) + KST_OFFSET_MS).getUTCDay();
  return weekday === 0 || weekday === 6;
}

// 지난달 단가는 등록할 수 없고 지난달 이벤트는 그 달 청구에 들어가지 않는다. 미래 이벤트는 청구 진단에 경고로 남는다.
export function checkUntil(until: Date, now: Date): void {
  if (until.getTime() > now.getTime()) throw new Error("--until은 지금보다 뒤일 수 없습니다. 미래 시각 이벤트는 청구 진단에 경고로 남습니다");
  if (kstMonth(until) !== kstMonth(now)) throw new Error("--until은 이번 달(한국 시간) 안이어야 합니다. 지난달 단가는 등록할 수 없고 지난달 이벤트는 그 달 청구에 들어가지 않습니다");
}

export function monthDatesUntil(until: Date): string[] {
  const last = kstDate(until);
  const dates: string[] = [];
  for (let cursor = kstMidnight(`${last.slice(0, 7)}-01`); kstDate(new Date(cursor)) <= last; cursor += DAY_MS) dates.push(kstDate(new Date(cursor)));
  return dates;
}

function tokens(random: Random, model: string, surface: SurfaceId): TokenProperties {
  const shape = SURFACE_TOKENS[surface];
  const kind = MODELS.find(item => item.id === model)?.kind;
  const text = TEXT_MODELS[model];
  if (kind !== "embedding" && !text) throw new Error(`모델 ${model}의 토큰 분포가 TEXT_MODELS에 없습니다`);
  const latencyOf = (base: number) => Math.max(1, Math.round(base * random.logNormal(1, 0.25)));
  if (kind === "embedding") {
    const input = bounded(random, surface === "batch" ? EMBEDDING_INPUT.batch : EMBEDDING_INPUT.api, 0.9, TOKEN_LIMITS.embedding_input_tokens);
    return { model, surface, input_tokens: input, output_tokens: 0, cache_read_tokens: 0, cache_write_tokens: 0, latency_ms: latencyOf(60 + input / 200) };
  }
  const output = bounded(random, shape.output * text.outputScale, 0.85, TOKEN_LIMITS.output_tokens);
  const hit = random.next() < shape.cacheHit;
  const cacheRead = hit ? bounded(random, text.context, 0.65, TOKEN_LIMITS.cache_read_tokens) : 0;
  const input = bounded(random, shape.input, 1, TOKEN_LIMITS.input_tokens);
  const writes = random.next() < (hit ? 0.55 : 0.6);
  const cacheWrite = writes ? bounded(random, hit ? 1800 : 12_000, hit ? 1 : 0.8, TOKEN_LIMITS.cache_write_tokens) : 0;
  const latency = latencyOf(text.baseLatency + output * text.perToken);
  return { model, surface, input_tokens: input, output_tokens: output, cache_read_tokens: cacheRead, cache_write_tokens: cacheWrite, latency_ms: latency };
}

function modelMixes(seed: string, customer: DemoCustomer): Partial<Record<SurfaceId, Weighted<string>>> {
  const mixes = MODEL_MIX[customer.usage];
  if (customer.size !== "small" && customer.size !== "new") return mixes;
  const allowed = createRandom(seed, customer.slug, "models").pick(COMPACT_MODEL_SETS);
  return Object.fromEntries(Object.entries(mixes).map(([surface, mix]) => [surface, mix.filter(([model]) => allowed.includes(model))]));
}

function dailyCount(seed: string, customer: DemoCustomer, date: string, random: Random): number {
  const profile = createRandom(seed, customer.slug, "profile");
  const [low, high] = DAILY_REQUESTS[customer.size];
  const base = profile.between(low, high);
  const weekendRatio = profile.between(...WEEKEND_RATIO[customer.usage]);
  const dayFactor = isWeekend(date) ? weekendRatio : 1;
  if (customer.size === "new") return Math.round(random.integer(0, high) * dayFactor);
  return Math.max(0, Math.round(base * dayFactor * random.logNormal(1, 0.12)));
}

// (씨앗, 고객, 날짜)마다 따로 만든 난수로 그날 하루치 전부를 만든다. 실행 시각과 무관하다.
export function generateDay(seed: string, customer: DemoCustomer, date: string): DemoEvent[] {
  if (customer.noUsage) return [];
  if (customer.startDay !== undefined && Number(date.slice(8, 10)) < customer.startDay) return [];
  const random = createRandom(seed, customer.slug, date);
  const count = dailyCount(seed, customer, date, random);
  const midnight = kstMidnight(date);
  const mixes = modelMixes(seed, customer);
  const drafts: Array<{ at: number; properties: TokenProperties }> = [];
  for (let index = 0; index < count; index++) {
    const surface = random.pick(SURFACE_MIX[customer.usage]);
    const model = random.pick(mixes[surface]!);
    const hours = customer.usage === "data" && surface === "batch" ? HOURS.night : customer.usage === "api" ? HOURS.service : HOURS.office;
    const hour = random.pick(hourWeights(hours));
    const at = midnight + hour * HOUR_MS + Math.floor(random.next() * HOUR_MS);
    drafts.push({ at, properties: tokens(random, model, surface) });
  }
  drafts.sort((left, right) => left.at - right.at);
  return drafts.map((draft, index) => ({
    customerSlug: customer.slug,
    transactionId: TRANSACTION_ID_PREFIX + digest(seed, customer.slug, date, index).slice(0, 32),
    type: EVENT_TYPE,
    timestamp: new Date(draft.at).toISOString(),
    properties: draft.properties,
  }));
}

// 이번 달 1일(한국 시간)부터 until 직전까지의 이벤트. 오늘은 하루치를 만든 뒤 until 이전 것만 남긴다.
export function generateEvents(seed: string, customers: readonly DemoCustomer[], until: Date): DemoEvent[] {
  const events: DemoEvent[] = [];
  for (const date of monthDatesUntil(until)) for (const customer of customers) {
    for (const event of generateDay(seed, customer, date)) if (Date.parse(event.timestamp) < until.getTime()) events.push(event);
  }
  return events;
}

export function eventsDigest(events: readonly DemoEvent[]): string {
  return digest(JSON.stringify(events));
}

// 그 달 1일 하루치의 지문. 1일이 끝나기 전이면 null이다.
export function firstDayDigest(seed: string, customers: readonly DemoCustomer[], until: Date): string | null {
  const endOfFirstDay = new Date(kstMidnight(`${kstMonth(until)}-01`) + DAY_MS);
  return until.getTime() < endOfFirstDay.getTime() ? null : eventsDigest(generateEvents(seed, customers, endOfFirstDay));
}

export type FirstDayRecord = { month: string; digest: string };

// 같은 씨앗인데 같은 달의 1일 하루치가 지난 실행과 다르면 생성기가 달라진 것이다(Node 버전 등). 거래 ID가 같아 달라진 이벤트는 중복으로 버려지므로 멈춘다.
// 달이 바뀌면 날짜가 달라 지문도 달라지는 것이 정상이다. 비교하지 않고 새 달의 지문으로 바꿔 남긴다.
export function checkFirstDay(saved: FirstDayRecord | undefined, seed: string, customers: readonly DemoCustomer[], until: Date): FirstDayRecord | undefined {
  const digest = firstDayDigest(seed, customers, until);
  if (!digest) return saved;
  const month = kstMonth(until);
  if (saved?.month === month && saved.digest !== digest) throw new Error("이벤트 생성기가 지난 실행과 다른 값을 냅니다. Node 버전이 같은지 확인하세요");
  return { month, digest };
}

export function usageRows(events: readonly DemoEvent[]): UsageRow[] {
  const totals = new Map<string, UsageRow>();
  for (const event of events) for (const metric of METRICS) {
    const quantity = event.properties[metric.property as keyof TokenProperties] as number;
    const key = `${event.customerSlug}|${metric.code}|${event.properties.model}|${event.properties.surface}`;
    const row = totals.get(key) ?? { customerSlug: event.customerSlug, metricCode: metric.code, model: event.properties.model, surface: event.properties.surface, quantity: 0 };
    row.quantity += quantity;
    totals.set(key, row);
  }
  return [...totals.values()];
}

export function summarize(events: readonly DemoEvent[]) {
  const count = (pick: (event: DemoEvent) => string) => {
    const result: Record<string, number> = {};
    for (const event of events) result[pick(event)] = (result[pick(event)] ?? 0) + 1;
    return Object.fromEntries(Object.entries(result).sort(([left], [right]) => left.localeCompare(right)));
  };
  const tokens = { input_tokens: 0, output_tokens: 0, cache_read_tokens: 0, cache_write_tokens: 0 };
  for (const event of events) for (const key of Object.keys(tokens) as Array<keyof typeof tokens>) tokens[key] += event.properties[key];
  return {
    events: events.length,
    byDate: count(event => kstDate(new Date(event.timestamp))),
    bySurface: count(event => event.properties.surface),
    byModel: count(event => event.properties.model),
    byModelSurface: count(event => `${event.properties.model}/${event.properties.surface}`),
    tokens,
  };
}
