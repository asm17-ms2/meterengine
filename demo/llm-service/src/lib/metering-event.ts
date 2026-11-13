import type { IngestEventRequest } from "@meterengine/sdk";
import { EVENT_TYPE, METRICS, transactionId } from "./catalog.ts";
import type { SurfaceId } from "./catalog.ts";
import type { TokenUsage } from "./contracts.ts";

export type UsageRecord = { requestId: string; customerId: string; occurredAt: string; modelId: string; surface: SurfaceId; usage: TokenUsage; latencyMs: number };

// MeterEngine에 보내는 속성은 이 함수에서만 만든다. 고객, 대화, 프롬프트를 가리키는 값은 넣지 않는다.
export function usageProperties(record: Pick<UsageRecord, "modelId" | "surface" | "usage" | "latencyMs">): Record<string, string | number> {
  return {
    model: record.modelId,
    surface: record.surface,
    ...Object.fromEntries(METRICS.map(metric => [metric.property, record.usage[metric.key]])),
    latency_ms: record.latencyMs,
  };
}

export function usageEvent(record: UsageRecord): IngestEventRequest {
  return {
    transaction_id: transactionId(record.requestId),
    customer_id: record.customerId,
    type: EVENT_TYPE,
    timestamp: record.occurredAt,
    properties: usageProperties(record),
  };
}
