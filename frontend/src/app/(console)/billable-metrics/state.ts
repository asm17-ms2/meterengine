export type BillableMetricRowView = {
  code: string;
  name: string;
  eventType: string;
  aggregation: string;
  targetProperty: string;
};

export type BillableMetricField = "code" | "name" | "event_type" | "target_property";

export type BillableMetricFormState =
  | { status: "idle" }
  | { status: "invalid"; fieldErrors: Partial<Record<BillableMetricField, string>> }
  | { status: "failed"; message: string }
  | { status: "done" };

export const BILLABLE_METRIC_FORM_IDLE: BillableMetricFormState = { status: "idle" };

export type BillableMetricDeleteState =
  | { status: "idle" }
  | { status: "failed"; message: string }
  | { status: "rejected"; reason: "events" | "policy"; name: string }
  | { status: "gone"; name: string }
  | { status: "done" };

export const BILLABLE_METRIC_DELETE_IDLE: BillableMetricDeleteState = { status: "idle" };
