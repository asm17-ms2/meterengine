import type { DraftInvoiceResponse, EventIssue, EventResponse, MeterEngineClient, UnbilledEventsResponse } from "@meterengine/sdk";
import type { Invoice } from "./diagnose-shared.ts";
import { PAGE_SIZE, activeMetersIn, collectPages, formatKst, usageCutoffOf } from "./diagnose-shared.ts";

// 서버가 거절 기록에 남기는 코드다. 이 밖의 코드가 생기면 other로 센다.
const REJECTION_CODES = ["customer_not_found", "customer_archived", "event_too_old", "validation_error"];

type Draft = Omit<DraftInvoiceResponse, "customers"> & {
  auto_finalize_enabled?: boolean;
  auto_finalize_at?: string | null;
  auto_finalize_running?: boolean;
  customers: Array<DraftInvoiceResponse["customers"][number] & { auto_finalize_held?: boolean }>;
};

interface MetricCount {
  billable_metric_code: string;
  event_count: number;
}

interface InvoiceCutoffCount {
  customer_id: string;
  customer_name: string;
  invoice_id: string;
  cutoff_at: string;
  event_count: number;
}

interface NegativeTotal {
  customer_id: string;
  customer_name: string;
  billable_metric_code: string;
  dimension_values: Record<string, unknown>;
  quantity: number;
}

interface SuspectedTypo {
  event_type: string;
  meter_event_type: string;
  billable_metric_codes: string[];
}

export interface MonthDiagnosis {
  month: string;
  counted_until: string;
  excluded_from_billing: {
    unknown_event_type: { event_count: number; types: Array<{ type: string; event_count: number }>; complete: boolean };
    invalid_target_property: { event_count: number; billable_metrics: MetricCount[]; complete: boolean };
    unpriced_usage: { event_count: number; billable_metrics: MetricCount[]; complete: boolean };
    received_after_cutoff: { event_count: number; cutoff_at: string; complete: boolean };
    after_invoice_cutoff: { event_count: number; customers: InvoiceCutoffCount[]; complete: boolean };
    negative_total: { combination_count: number; combinations: NegativeTotal[]; complete: boolean };
  };
  not_yet_counted: { future_timestamp: UnbilledEventsResponse["future_timestamp"] };
  counted_negative_events: UnbilledEventsResponse["negative_quantity"];
  rejected: { count: number; codes: Record<string, number>; recording_started_at: string | null };
  types_without_meter: string[];
  suspected_typos: SuspectedTypo[];
  finalization: {
    usage_cutoff_at: string;
    auto_finalize_enabled: boolean | null;
    auto_finalize_at: string | null;
    auto_finalize_running: boolean | null;
    held_customers: Array<{ customer_id: string; customer_name: string }>;
  };
}

function editDistance(left: string, right: string): number {
  let previous = Array.from({ length: right.length + 1 }, (_, index) => index);
  for (let i = 1; i <= left.length; i += 1) {
    const current = [i];
    for (let j = 1; j <= right.length; j += 1) {
      current[j] = Math.min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (left[i - 1] === right[j - 1] ? 0 : 1));
    }
    previous = current;
  }
  return previous[right.length];
}

// 대소문자나 구분 기호만 다르거나 글자 한두 개가 다르면 오타로 의심한다. 짧은 유형은 한 글자까지만 본다.
export function looksLikeTypo(type: string, meterType: string): boolean {
  const normalize = (text: string) => text.toLowerCase().replace(/[-_.\s]/g, "");
  if (normalize(type) === normalize(meterType)) return true;
  return editDistance(type, meterType) <= (Math.min(type.length, meterType.length) >= 6 ? 2 : 1);
}

function countByMetric(issues: EventIssue[]): MetricCount[] {
  const counts = new Map<string, number>();
  for (const issue of issues) {
    if (issue.billable_metric_code !== null) counts.set(issue.billable_metric_code, (counts.get(issue.billable_metric_code) ?? 0) + 1);
  }
  return [...counts].sort(([left], [right]) => left.localeCompare(right)).map(([code, count]) => ({ billable_metric_code: code, event_count: count }));
}

async function unpricedUsage(client: MeterEngineClient, month: string) {
  const { items, complete } = await collectPages(async (page) => {
    const result = await client.searchEventIssues({ month, reason: "unpriced_usage", page, size: PAGE_SIZE });
    return { items: result.issues, total: result.total };
  });
  return { event_count: new Set(items.map((issue) => issue.transaction_id)).size, billable_metrics: countByMetric(items), complete };
}

async function rejected(client: MeterEngineClient, month: string) {
  const all = await client.searchEventRejections({ month, page: 0, size: 1 });
  const codes: Record<string, number> = {};
  if (all.total > 0) {
    for (const code of REJECTION_CODES) {
      const { total } = await client.searchEventRejections({ month, error_code: code, page: 0, size: 1 });
      if (total > 0) codes[code] = total;
    }
    const other = all.total - Object.values(codes).reduce((sum, count) => sum + count, 0);
    if (other > 0) codes.other = other;
  }
  return { count: all.total, codes, recording_started_at: all.recording_started_at };
}

async function receivedAfterCutoff(client: MeterEngineClient, month: string, now: Date) {
  const cutoff = usageCutoffOf(month);
  const summary = { event_count: 0, cutoff_at: formatKst(cutoff), complete: true };
  if (now.getTime() < cutoff.getTime()) return summary;
  const isLate = (event: EventResponse) => Date.parse(event.received_at) >= cutoff.getTime();
  const { items, complete } = await collectPages(
    async (page) => {
      const result = await client.listEvents({ month, sort: "received_at", order: "desc", page, size: PAGE_SIZE });
      return { items: result.events, total: result.total };
    },
    (events) => !events.every(isLate),
  );
  return { ...summary, event_count: items.filter(isLate).length, complete };
}

async function typeCheck(client: MeterEngineClient, month: string) {
  const { types } = await client.listEventTypes({ month });
  const active = await activeMetersIn(client, month);
  const meterTypes = new Set(active.map((meter) => meter.event_type));
  const withoutMeter = types.filter((type) => !meterTypes.has(type));
  const suspected = withoutMeter.flatMap((type) =>
    [...meterTypes]
      .filter((meterType) => looksLikeTypo(type, meterType))
      .map((meterType) => ({
        event_type: type,
        meter_event_type: meterType,
        billable_metric_codes: active.filter((meter) => meter.event_type === meterType).map((meter) => meter.code),
      })),
  );
  return { withoutMeter, suspected };
}

// 마감 전에 확정한 고객은 확정 시각 뒤에 받거나 발생한 그 달 이벤트가 청구에 들지 않는다. 집계 마감 뒤에 받은 것은 received_after_cutoff에서 센다.
async function afterInvoiceCutoff(client: MeterEngineClient, month: string) {
  const usageCutoff = usageCutoffOf(month).getTime();
  const { invoices } = await client.listInvoices({ month });
  const customers: InvoiceCutoffCount[] = [];
  let isComplete = true;
  for (const invoice of invoices as Invoice[]) {
    if (invoice.month !== month || invoice.cutoff_at === undefined || Date.parse(invoice.cutoff_at) >= usageCutoff) continue;
    const cutoff = Date.parse(invoice.cutoff_at);
    const isLate = (event: EventResponse) => Date.parse(event.received_at) < usageCutoff && (Date.parse(event.received_at) >= cutoff || Date.parse(event.occurred_at) >= cutoff);
    const { items, complete } = await collectPages(
      async (page) => {
        const result = await client.listEvents({ month, customer_id: invoice.customer_id, sort: "received_at", order: "desc", page, size: PAGE_SIZE });
        return { items: result.events, total: result.total };
      },
      (events) => events.some((event) => Date.parse(event.received_at) < cutoff),
    );
    isComplete &&= complete;
    const count = items.filter(isLate).length;
    if (count > 0) customers.push({ customer_id: invoice.customer_id, customer_name: invoice.customer_name, invoice_id: invoice.id, cutoff_at: invoice.cutoff_at, event_count: count });
  }
  return { event_count: customers.reduce((sum, customer) => sum + customer.event_count, 0), customers, complete: isComplete };
}

function negativeTotals(draft: Draft) {
  const combinations = draft.customers.flatMap((customer) =>
    customer.negative_usages.map((usage) => ({
      customer_id: customer.customer_id,
      customer_name: customer.customer_name,
      billable_metric_code: usage.billable_metric_code,
      dimension_values: usage.dimension_values,
      quantity: usage.quantity,
    })),
  );
  return { combination_count: combinations.length, combinations, complete: true };
}

function finalization(draft: Draft, month: string) {
  return {
    usage_cutoff_at: formatKst(usageCutoffOf(month)),
    auto_finalize_enabled: draft.auto_finalize_enabled ?? null,
    auto_finalize_at: draft.auto_finalize_at ?? null,
    auto_finalize_running: draft.auto_finalize_running ?? null,
    held_customers: draft.customers.filter((customer) => customer.auto_finalize_held === true).map((customer) => ({ customer_id: customer.customer_id, customer_name: customer.customer_name })),
  };
}

export async function diagnoseMonth(client: MeterEngineClient, month: string, now: Date): Promise<MonthDiagnosis> {
  const unbilled = await client.getUnbilledEvents({ month });
  const unpriced = await unpricedUsage(client, month);
  const rejections = await rejected(client, month);
  const types = await typeCheck(client, month);
  const late = await receivedAfterCutoff(client, month, now);
  const invoiceLate = await afterInvoiceCutoff(client, month);
  const draft = (await client.previewDraftInvoice({ month })) as Draft;
  const unmatched = unbilled.unmatched_type;
  return {
    month,
    counted_until: unbilled.cutoff_at,
    excluded_from_billing: {
      unknown_event_type: {
        event_count: unmatched.event_count,
        types: unmatched.types,
        complete: unmatched.types.reduce((sum, type) => sum + type.event_count, 0) === unmatched.event_count,
      },
      // 서버는 이 집계와 청구 예정액을 자르지 않아 늘 끝까지 센 값이다. 칸 모양만 다른 항목과 맞춘다.
      invalid_target_property: { ...unbilled.non_numeric_value, complete: true },
      unpriced_usage: unpriced,
      received_after_cutoff: late,
      after_invoice_cutoff: invoiceLate,
      negative_total: negativeTotals(draft),
    },
    not_yet_counted: { future_timestamp: unbilled.future_timestamp },
    counted_negative_events: unbilled.negative_quantity,
    rejected: rejections,
    types_without_meter: types.withoutMeter,
    suspected_typos: types.suspected,
    finalization: finalization(draft, month),
  };
}
