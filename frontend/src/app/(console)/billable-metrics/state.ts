import type { MetricRoundingMode } from "@/lib/billable-metric-calculation";

export type BillableMetricRowView = {
  code: string;
  name: string;
  eventType: string;
  aggregation: string;
  targetProperty: string | null;
  percentile?: number | null;
  unitSize?: number;
  roundingMode?: MetricRoundingMode;
  roundingPrecision?: number;
  dimensionProperties: string[];
  archivedAt?: string | null;
  activeFrom?: string | null;
  inactiveFrom?: string | null;
  isReactivationScheduled?: boolean;
};

export type BillableMetricField =
  | "code"
  | "name"
  | "event_type"
  | "aggregation"
  | "target_property"
  | "percentile"
  | "unit_size"
  | "rounding_mode"
  | "rounding_precision"
  | "dimension_properties";

export type BillableMetricFormState =
  | { status: "idle" }
  | { status: "invalid"; fieldErrors: Partial<Record<BillableMetricField, string>> }
  | { status: "failed"; message: string }
  | { status: "done" };

export const BILLABLE_METRIC_FORM_IDLE: BillableMetricFormState = { status: "idle" };

export function billableMetricFailureMessage(code: string, message: string): string {
  return code === "billable_metric_has_events" ? "수집된 이벤트가 있어 이름 외의 계산 기준은 바꿀 수 없습니다. 계산 기준을 바꾸려면 새 미터를 등록하세요." : message;
}

export type BillableMetricDeleteState =
  | { status: "idle" }
  | { status: "failed"; message: string }
  | { status: "rejected"; reason: "events" | "price_rates"; name: string }
  | { status: "gone"; name: string }
  | { status: "done" };

export const BILLABLE_METRIC_DELETE_IDLE: BillableMetricDeleteState = { status: "idle" };
