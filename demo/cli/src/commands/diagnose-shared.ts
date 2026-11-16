import type { InvoiceResponse, MeterEngineClient } from "@meterengine/sdk";

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
export const PAGE_SIZE = 100;
// 진단 한 번이 서버에 보내는 페이지 요청의 상한이다. 넘으면 complete를 false로 낸다.
export const MAX_PAGES = 10;

export type Invoice = InvoiceResponse & { cutoff_at?: string };

export interface ActiveMeter {
  code: string;
  event_type: string;
}

export interface Collected<T> {
  items: T[];
  complete: boolean;
}

// 서버는 그 달에 발생한 이벤트 중 다음 달 2일 00:00 KST 전에 받은 것만 집계하고 청구한다.
export function usageCutoffOf(month: string): Date {
  const [year, value] = month.split("-").map(Number);
  return new Date(Date.UTC(year, value, 2) - KST_OFFSET_MS);
}

export function formatKst(instant: Date): string {
  return new Date(instant.getTime() + KST_OFFSET_MS).toISOString().replace(/(\.000)?Z$/, "+09:00");
}

// 그 달에 집계하는 미터는 서버의 사용량 응답에서 고른다. 보관했다 복원한 미터의 지난 기간은 미터 목록에 없다.
export async function activeMetersIn(client: MeterEngineClient, month: string): Promise<ActiveMeter[]> {
  const { billable_metric_usages: usages } = await client.aggregateBillableMetricUsages({ month });
  return usages.map((usage) => ({ code: usage.code, event_type: usage.event_type }));
}

function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object") {
    const entries = Object.entries(value).sort(([left], [right]) => left.localeCompare(right));
    return `{${entries.map(([key, item]) => `${JSON.stringify(key)}:${canonical(item)}`).join(",")}}`;
  }
  return JSON.stringify(value ?? null);
}

// 서버는 미터의 차원 값을 이벤트 속성 그대로 담고, 속성이 없으면 null로 담는다.
export function sameDimensions(dimensionValues: Record<string, unknown>, properties: Record<string, unknown>): boolean {
  return Object.entries(dimensionValues).every(([key, value]) => canonical(value) === canonical(properties[key]));
}

// 거절 기록을 시작한 시각이 찾은 달 중 가장 이른 달의 시작보다 늦으면 그 사이 거절은 기록에 없다.
export function recordingStartedAfter(months: string[], recordingStartedAt: string | null): boolean {
  if (recordingStartedAt === null || months.length === 0) return false;
  const earliest = [...months].sort()[0];
  return Date.parse(recordingStartedAt) > Date.parse(`${earliest}-01T00:00:00+09:00`);
}

export async function collectPages<T>(fetchPage: (page: number) => Promise<{ items: T[]; total: number }>, isDone: (items: T[]) => boolean = () => false): Promise<Collected<T>> {
  const items: T[] = [];
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const result = await fetchPage(page);
    items.push(...result.items);
    if ((page + 1) * PAGE_SIZE >= result.total || result.items.length === 0 || isDone(result.items)) return { items, complete: true };
  }
  return { items, complete: false };
}
