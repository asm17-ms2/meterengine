"use server";

import type {
  MetricField,
  MetricFormState,
} from "@/app/(console)/metrics/state";
import type { ApiError } from "@/lib/api/client";
import { registerMetric } from "@/lib/api/metrics";

const AGGREGATION = "SUM";

const METRIC_FIELDS: readonly MetricField[] = [
  "code",
  "name",
  "event_type",
  "target_property",
];

const REQUIRED_MESSAGES: Record<MetricField, string> = {
  code: "코드를 입력하세요",
  name: "이름을 입력하세요",
  event_type: "이벤트 타입을 입력하세요",
  target_property: "집계 대상 속성을 입력하세요. SUM 집계는 필수입니다",
};

function readField(formData: FormData, field: MetricField): string {
  const raw = formData.get(field);
  return typeof raw === "string" ? raw.trim() : "";
}

function isMetricField(field: string): field is MetricField {
  return (METRIC_FIELDS as readonly string[]).includes(field);
}

function serverFieldErrors(
  error: ApiError,
): Partial<Record<MetricField, string>> {
  const fieldErrors: Partial<Record<MetricField, string>> = {};
  for (const entry of error.errors ?? []) {
    if (isMetricField(entry.field)) fieldErrors[entry.field] = entry.message;
  }
  return fieldErrors;
}

function failureState(error: ApiError): MetricFormState {
  if (error.code === "metric_already_exists") {
    return {
      status: "invalid",
      fieldErrors: { code: "이미 사용 중인 코드입니다. 다른 코드를 쓰세요." },
    };
  }
  if (error.code === "validation_error") {
    const fieldErrors = serverFieldErrors(error);
    if (Object.keys(fieldErrors).length > 0) {
      return { status: "invalid", fieldErrors };
    }
    return { status: "failed", message: "입력값을 확인해주세요." };
  }
  switch (error.code) {
    case "invalid_billable_metric":
      return {
        status: "failed",
        message: "집계 설정이 올바르지 않습니다. 집계 함수는 SUM만 지원하고 집계 대상 속성이 필요합니다.",
      };
    case "unknown_organization":
      return {
        status: "failed",
        message: "도입사를 찾을 수 없습니다. 설정을 확인해주세요.",
      };
    case "network_error":
      return { status: "failed", message: error.title };
    default:
      return {
        status: "failed",
        message: "저장하지 못했습니다. 잠시 후 다시 시도해주세요.",
      };
  }
}

export async function registerMetricAction(
  _prev: MetricFormState,
  formData: FormData,
): Promise<MetricFormState> {
  const fieldErrors: Partial<Record<MetricField, string>> = {};
  const values = {} as Record<MetricField, string>;
  for (const field of METRIC_FIELDS) {
    values[field] = readField(formData, field);
    if (values[field] === "") fieldErrors[field] = REQUIRED_MESSAGES[field];
  }
  if (Object.keys(fieldErrors).length > 0) {
    return { status: "invalid", fieldErrors };
  }

  const result = await registerMetric({
    code: values.code,
    name: values.name,
    event_type: values.event_type,
    aggregation: AGGREGATION,
    target_property: values.target_property,
  });
  if (!result.ok) return failureState(result.error);

  return {
    status: "done",
    metric: {
      code: result.data.code,
      name: result.data.name,
      eventType: result.data.event_type,
      aggregation: result.data.aggregation,
      targetProperty: result.data.target_property,
    },
  };
}
