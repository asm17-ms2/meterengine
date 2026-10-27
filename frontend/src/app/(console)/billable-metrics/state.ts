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

export type BillableMetricDeleteState =
  | { status: "idle" }
  | { status: "failed"; message: string }
  | { status: "rejected"; reason: "events" | "price_rates"; name: string }
  | { status: "gone"; name: string }
  | { status: "done" };

export const BILLABLE_METRIC_DELETE_IDLE: BillableMetricDeleteState = { status: "idle" };
