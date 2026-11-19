import { MeterEngineApiError } from "@meterengine/sdk";
import type { EventIssue, EventRejection, EventResponse, MeterEngineClient } from "@meterengine/sdk";
import { kstMonthOf } from "../month.ts";
import type { Invoice } from "./diagnose-shared.ts";
import { PAGE_SIZE, activeMetersIn, collectPages, formatKst, recordingStartedAfter, sameDimensions, usageCutoffOf } from "./diagnose-shared.ts";

// 조회 API의 q 칸 길이 상한이다. 거절 기록도 이보다 긴 transaction_id는 남기지 않는다.
const SEARCH_TEXT_LIMIT = 255;

const REJECTION_HINTS: Record<string, string> = {
  customer_not_found: "customer_id에 MeterEngine 고객 UUID를 넣었는지 확인하세요. 외부 ID라면 customers list --external-id로 UUID를 찾습니다",
  customer_archived: "보관한 고객에게는 새 이벤트를 받지 않습니다. 고객 상태를 확인하세요",
  event_too_old: "timestamp가 받은 시각보다 35일 넘게 과거라 받지 않았습니다. 같은 이벤트를 다시 보내도 저장되지 않습니다",
  validation_error: "error_fields의 칸을 고쳐 같은 transaction_id로 다시 보내세요",
};

export interface Finding {
  source: "event" | "invoice" | "draft_invoice" | "event_issues" | "event_rejections" | "none";
  reason: string;
  [key: string]: unknown;
}

export interface EventDiagnosis {
  transaction_id: string;
  stored: boolean;
  billable: boolean | null;
  month: string | null;
  billable_metrics?: { included: string[]; excluded: string[] };
  invoice?: { id: string; invoice_number: string; finalized_at: string; cutoff_at: string | null };
  findings: Finding[];
  notes: string[];
}

async function lookup(client: MeterEngineClient, transactionId: string): Promise<EventResponse | null> {
  try {
    return await client.getEvent(transactionId);
  } catch (error) {
    if (error instanceof MeterEngineApiError && error.status === 404 && error.code === "event_not_found") return null;
    throw error;
  }
}

function issueFinding(issue: EventIssue): Finding {
  return {
    source: "event_issues",
    reason: issue.reason,
    type: issue.type,
    billable_metric_code: issue.billable_metric_code,
    target_property: issue.target_property,
    action_hint: issue.action_hint,
  };
}

function rejectionFinding(rejection: EventRejection): Finding {
  return {
    source: "event_rejections",
    reason: rejection.error_code,
    error_fields: rejection.error_fields,
    recorded_at: rejection.recorded_at,
    batch_index: rejection.batch_index,
    action_hint: REJECTION_HINTS[rejection.error_code] ?? "거절 코드에 맞게 이벤트를 고쳐 다시 보내세요",
  };
}

async function findInvoice(client: MeterEngineClient, event: EventResponse, month: string): Promise<Invoice | undefined> {
  const { invoices } = await client.listInvoices({ month, customer_id: event.customer_id });
  return (invoices as Invoice[]).find((invoice) => invoice.customer_id === event.customer_id && invoice.month === month);
}

async function findIssues(client: MeterEngineClient, event: EventResponse, month: string) {
  const query = event.transaction_id.length <= SEARCH_TEXT_LIMIT ? { q: event.transaction_id } : {};
  const collected = await collectPages(async (page) => {
    const result = await client.searchEventIssues({ month, customer_id: event.customer_id, ...query, page, size: PAGE_SIZE });
    return { items: result.issues, total: result.total };
  });
  return { issues: collected.items.filter((issue) => issue.transaction_id === event.transaction_id), complete: collected.complete };
}

async function meterCodesFor(client: MeterEngineClient, type: string, month: string): Promise<string[]> {
  return (await activeMetersIn(client, month)).filter((meter) => meter.event_type === type).map((meter) => meter.code);
}

// 고객, 과금 미터, 차원 값 조합의 그 달 합계가 음수면 서버는 그 조합을 금액에 넣지 않는다.
async function negativeTotalFindings(client: MeterEngineClient, event: EventResponse, month: string, codes: string[]): Promise<Finding[]> {
  const draft = await client.previewDraftInvoice({ month });
  const customer = draft.customers.find((item) => item.customer_id === event.customer_id);
  return (customer?.negative_usages ?? [])
    .filter((usage) => codes.includes(usage.billable_metric_code) && sameDimensions(usage.dimension_values, event.properties))
    .map((usage) => ({
      source: "draft_invoice",
      reason: "negative_total",
      billable_metric_code: usage.billable_metric_code,
      dimension_values: usage.dimension_values,
      quantity: usage.quantity,
      action_hint: "이 고객의 이 과금 미터와 차원 값 조합은 그 달 합계가 음수라 금액에 들지 않습니다. 정정 이벤트의 수량을 확인하세요",
    }));
}

// 확정한 인보이스는 바뀌지 않으므로, 이벤트의 과금 미터와 차원 값이 금액 라인에 있어야 청구된 것이다.
function invoiceLineFindings(event: EventResponse, invoice: Invoice, codes: string[], issues: EventIssue[]): Finding[] {
  return codes.flatMap((code) => {
    if (invoice.lines.some((line) => line.billable_metric_code === code && sameDimensions(line.dimension_values, event.properties))) return [];
    const unpriced = issues.filter((issue) => issue.billable_metric_code === code && issue.reason === "unpriced_usage");
    if (unpriced.length > 0) return unpriced.map(issueFinding);
    return [
      {
        source: "invoice",
        reason: "not_in_finalized_invoice",
        billable_metric_code: code,
        invoice_id: invoice.id,
        action_hint: "확정한 인보이스에 이 과금 미터와 차원 값의 금액 라인이 없습니다. 확정 당시 단가가 없었거나 합계가 0 이하였을 수 있고, 확정한 인보이스는 바뀌지 않습니다",
      },
    ];
  });
}

// 받은 시각이 마감을 넘긴 이벤트는 저장돼도 집계와 청구에 들지 않는다.
function timingFinding(event: EventResponse, month: string, now: Date): Finding | undefined {
  if (Date.parse(event.occurred_at) >= now.getTime()) {
    return {
      source: "event",
      reason: "future_timestamp",
      occurred_at: event.occurred_at,
      action_hint: "발생 시각이 아직 오지 않아 집계 전입니다. 그 시각이 지나면 집계에 들어가니 그 뒤에 다시 진단하세요",
    };
  }
  const cutoff = usageCutoffOf(month);
  if (Date.parse(event.received_at) >= cutoff.getTime()) {
    return {
      source: "event",
      reason: "received_after_cutoff",
      received_at: event.received_at,
      cutoff_at: formatKst(cutoff),
      action_hint: "그 달의 집계 마감 뒤에 받은 이벤트라 저장됐지만 청구에 들지 않습니다. 마감 전에 보내도록 전송 지연을 줄이세요",
    };
  }
  return undefined;
}

function invoiceFinding(event: EventResponse, invoice: Invoice): Finding | undefined {
  if (invoice.cutoff_at === undefined) return undefined;
  const cutoff = Date.parse(invoice.cutoff_at);
  if (Date.parse(event.received_at) < cutoff && Date.parse(event.occurred_at) < cutoff) return undefined;
  return {
    source: "invoice",
    reason: "after_invoice_cutoff",
    invoice_id: invoice.id,
    cutoff_at: invoice.cutoff_at,
    received_at: event.received_at,
    action_hint: "이 고객의 그 달 인보이스를 이 이벤트를 받기 전에 확정해 금액에 들지 않았습니다. 확정한 인보이스는 바뀌지 않습니다",
  };
}

function findingCodes(findings: Finding[]): string[] {
  return [...new Set(findings.flatMap((finding) => (typeof finding.billable_metric_code === "string" ? [finding.billable_metric_code] : [])))];
}

async function diagnoseStored(client: MeterEngineClient, event: EventResponse, now: Date): Promise<EventDiagnosis> {
  const month = kstMonthOf(new Date(event.occurred_at));
  const result: EventDiagnosis = { transaction_id: event.transaction_id, stored: true, billable: true, month, findings: [], notes: [] };

  const timing = timingFinding(event, month, now);
  if (timing !== undefined) {
    result.findings.push(timing);
    result.billable = timing.reason === "future_timestamp" ? null : false;
    return result;
  }

  const invoice = await findInvoice(client, event, month);
  if (invoice !== undefined) {
    result.invoice = { id: invoice.id, invoice_number: invoice.invoice_number, finalized_at: invoice.finalized_at, cutoff_at: invoice.cutoff_at ?? null };
    const late = invoiceFinding(event, invoice);
    if (late !== undefined) {
      result.findings.push(late);
      result.billable = false;
      return result;
    }
    result.notes.push("인보이스를 확정한 달이라 확정한 인보이스의 금액 라인에 이 이벤트의 과금 미터와 차원 값이 있는지로 판정했습니다. 확정한 금액은 바뀌지 않습니다");
  }

  const { issues, complete } = await findIssues(client, event, month);
  if (!complete) result.notes.push("청구 누락 사유를 조회 상한까지만 확인했습니다. 빠진 사유가 있을 수 있습니다");
  if (issues.some((issue) => issue.reason === "unknown_event_type")) {
    result.findings.push(...issues.map(issueFinding));
    result.billable = false;
    return result;
  }

  const meterCodes = await meterCodesFor(client, event.type, month);
  if (invoice === undefined) {
    result.findings.push(...issues.map(issueFinding));
    const candidates = meterCodes.filter((code) => !findingCodes(result.findings).includes(code));
    if (candidates.length > 0) result.findings.push(...(await negativeTotalFindings(client, event, month, candidates)));
  } else {
    // 단가 사유는 지금 단가로 다시 계산한 것이라, 확정한 달은 이벤트 값 사유만 그대로 쓰고 나머지는 인보이스 라인으로 본다.
    const invalid = issues.filter((issue) => issue.reason !== "unpriced_usage");
    result.findings.push(...invalid.map(issueFinding));
    const checked = meterCodes.filter((code) => !invalid.some((issue) => issue.billable_metric_code === code));
    result.findings.push(...invoiceLineFindings(event, invoice, checked, issues));
  }

  const excluded = findingCodes(result.findings);
  const included = meterCodes.filter((code) => !excluded.includes(code));
  if (meterCodes.length > 0 || excluded.length > 0) result.billable_metrics = { included, excluded };
  if (included.length > 0) result.billable = true;
  else if (excluded.length > 0) result.billable = false;
  else {
    result.billable = null;
    result.notes.push("이 유형을 집계하는 과금 미터를 찾지 못했지만 서버가 청구 누락 사유도 남기지 않아 판정하지 못했습니다");
  }
  if (!complete && result.billable === true) result.billable = null;
  return result;
}

async function findRejections(client: MeterEngineClient, transactionId: string, months: string[]) {
  const findings: Finding[] = [];
  let recordingStartedAt: string | null = null;
  let isComplete = true;
  if (transactionId.length > SEARCH_TEXT_LIMIT) return { findings, recordingStartedAt, complete: isComplete };
  for (const month of months) {
    const collected = await collectPages(async (page) => {
      const result = await client.searchEventRejections({ month, q: transactionId, page, size: PAGE_SIZE });
      recordingStartedAt = result.recording_started_at;
      return { items: result.rejections, total: result.total };
    });
    isComplete &&= collected.complete;
    findings.push(...collected.items.filter((rejection) => rejection.transaction_id === transactionId).map(rejectionFinding));
  }
  return { findings, recordingStartedAt, complete: isComplete };
}

function possibleCauses(months: string[], recordingStartedAt: string | null, transactionId: string) {
  const causes = [
    { cause: "rejected_without_record", hint: "요청 전체의 JSON 형식과 입력 검증, 인증, 요청 한도 단계에서 거절하면 서버에 기록이 남지 않습니다. 보낼 때 받은 응답의 code를 확인하세요" },
    { cause: "accepted_not_yet_stored", hint: "서버가 202로 받았다면 저장은 뒤에서 이어지므로 아직 조회에 없을 수 있습니다. 잠시 뒤 다시 진단하세요" },
    { cause: "not_delivered", hint: "연결 실패나 시간 초과로 서버에 닿지 않았을 수 있습니다. 같은 transaction_id로 다시 보내면 중복 없이 한 번만 저장됩니다" },
    { cause: "rejected_in_other_month", hint: `거절 기록은 거절한 달로 찾습니다. ${months.join(", ")} 밖에서 보냈다면 --month로 그 달을 지정하세요` },
  ];
  if (recordingStartedAfter(months, recordingStartedAt)) {
    causes.push({ cause: "rejected_before_recording", hint: `서버는 ${recordingStartedAt}부터 거절을 기록했습니다. 그 전에 거절된 요청은 기록에 없습니다` });
  }
  if (transactionId.length > SEARCH_TEXT_LIMIT) causes.push({ cause: "transaction_id_too_long", hint: `${SEARCH_TEXT_LIMIT}자를 넘는 transaction_id는 거절 기록에 남지 않아 찾을 수 없습니다` });
  return causes;
}

export async function diagnoseEvent(client: MeterEngineClient, transactionId: string, rejectionMonths: string[], now: Date): Promise<EventDiagnosis> {
  const event = await lookup(client, transactionId);
  if (event !== null) return diagnoseStored(client, event, now);

  const { findings, recordingStartedAt, complete } = await findRejections(client, transactionId, rejectionMonths);
  const result: EventDiagnosis = { transaction_id: transactionId, stored: false, billable: false, month: null, findings, notes: [] };
  if (!complete) result.notes.push("거절 기록을 조회 상한까지만 확인했습니다");
  if (findings.length > 0) return result;
  result.billable = null;
  result.findings.push({ source: "none", reason: "no_record", searched_months: rejectionMonths, possible_causes: possibleCauses(rejectionMonths, recordingStartedAt, transactionId) });
  return result;
}
