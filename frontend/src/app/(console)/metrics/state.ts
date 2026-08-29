export type MetricRowView = {
  code: string;
  name: string;
  eventType: string;
  aggregation: string;
  targetProperty: string;
};

export type MetricField = "code" | "name" | "event_type" | "target_property";

export type MetricFormState =
  | { status: "idle" }
  | { status: "invalid"; fieldErrors: Partial<Record<MetricField, string>> }
  | { status: "failed"; message: string }
  | { status: "done" };

export const METRIC_FORM_IDLE: MetricFormState = { status: "idle" };

export type MetricDeleteState =
  | { status: "idle" }
  | { status: "failed"; message: string }
  | { status: "rejected"; reason: "events" | "policy"; name: string }
  | { status: "gone"; name: string }
  | { status: "done" };

export const METRIC_DELETE_IDLE: MetricDeleteState = { status: "idle" };
