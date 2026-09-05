"use server";

import { revalidatePath } from "next/cache";

import type {
  BillableMetricDeleteState,
  BillableMetricField,
  BillableMetricFormState,
} from "@/app/(console)/billable-metrics/state";
import type { ApiError } from "@/lib/api/client";
import { createBillableMetric, deleteBillableMetric, updateBillableMetric } from "@/lib/api/billable-metrics";

const AGGREGATION = "SUM";

const BILLABLE_METRIC_FIELDS: readonly BillableMetricField[] = [
  "code",
  "name",
  "event_type",
  "target_property",
];

const REQUIRED_MESSAGES: Record<BillableMetricField, string> = {
  code: "코드를 입력하세요",
  name: "이름을 입력하세요",
  event_type: "이벤트 타입을 입력하세요",
  target_property: "집계 대상 속성을 입력하세요. SUM 집계는 필수입니다",
};

function readField(formData: FormData, field: BillableMetricField): string {
  const raw = formData.get(field);
  return typeof raw === "string" ? raw.trim() : "";
}

function isBillableMetricField(field: string): field is BillableMetricField {
  return (BILLABLE_METRIC_FIELDS as readonly string[]).includes(field);
}

function serverFieldErrors(
  error: ApiError,
): Partial<Record<BillableMetricField, string>> {
  const fieldErrors: Partial<Record<BillableMetricField, string>> = {};
  for (const entry of error.errors ?? []) {
    if (isBillableMetricField(entry.field)) fieldErrors[entry.field] = entry.message;
  }
  return fieldErrors;
}

function failureState(error: ApiError): BillableMetricFormState {
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

export async function createBillableMetricAction(
  _prev: BillableMetricFormState,
  formData: FormData,
): Promise<BillableMetricFormState> {
  const fieldErrors: Partial<Record<BillableMetricField, string>> = {};
  const values = {} as Record<BillableMetricField, string>;
  for (const field of BILLABLE_METRIC_FIELDS) {
    values[field] = readField(formData, field);
    if (values[field] === "") fieldErrors[field] = REQUIRED_MESSAGES[field];
  }
  if (Object.keys(fieldErrors).length > 0) {
    return { status: "invalid", fieldErrors };
  }

  const result = await createBillableMetric({
    code: values.code,
    name: values.name,
    event_type: values.event_type,
    aggregation: AGGREGATION,
    target_property: values.target_property,
  });
  if (!result.ok) return failureState(result.error);

  revalidatePath("/billable-metrics");
  return { status: "done" };
}

const UPDATE_FIELDS: readonly BillableMetricField[] = [
  "name",
  "event_type",
  "target_property",
];

function updateFailureState(error: ApiError): BillableMetricFormState {
  if (error.code === "metric_not_found") {
    return {
      status: "failed",
      message: "이미 삭제된 미터입니다. 목록을 새로 고쳐주세요.",
    };
  }
  if (error.code === "metric_basis_has_events") {
    return {
      status: "failed",
      message:
        "이벤트가 잡히는 미터라 이벤트 타입과 집계 대상 속성을 바꿀 수 없습니다. 이름만 고치거나, 지우고 다시 등록하세요.",
    };
  }
  return failureState(error);
}

export async function updateBillableMetricAction(
  _prev: BillableMetricFormState,
  formData: FormData,
): Promise<BillableMetricFormState> {
  const code = formData.get("code");
  if (typeof code !== "string" || code === "") {
    return { status: "failed", message: "미터를 특정하지 못했습니다." };
  }

  const fieldErrors: Partial<Record<BillableMetricField, string>> = {};
  const values = {} as Record<BillableMetricField, string>;
  for (const field of UPDATE_FIELDS) {
    values[field] = readField(formData, field);
    if (values[field] === "") fieldErrors[field] = REQUIRED_MESSAGES[field];
  }
  if (Object.keys(fieldErrors).length > 0) {
    return { status: "invalid", fieldErrors };
  }

  const result = await updateBillableMetric(code, {
    name: values.name,
    event_type: values.event_type,
    aggregation: AGGREGATION,
    target_property: values.target_property,
  });
  if (!result.ok) return updateFailureState(result.error);

  revalidatePath("/billable-metrics");
  return { status: "done" };
}

export async function deleteBillableMetricAction(
  _prev: BillableMetricDeleteState,
  formData: FormData,
): Promise<BillableMetricDeleteState> {
  const code = formData.get("code");
  const name = formData.get("name");
  const label = typeof name === "string" ? name : "";
  if (typeof code !== "string" || code === "") {
    return { status: "failed", message: "미터를 특정하지 못했습니다." };
  }

  const result = await deleteBillableMetric(code);
  if (!result.ok) {
    if (result.error.code === "metric_has_events") {
      return { status: "rejected", reason: "events", name: label };
    }
    if (result.error.code === "metric_has_price_policy") {
      return { status: "rejected", reason: "policy", name: label };
    }
    if (result.error.code === "metric_not_found") {
      revalidatePath("/billable-metrics");
      return { status: "gone", name: label };
    }
    return {
      status: "failed",
      message:
        result.error.code === "network_error"
          ? result.error.title
          : "삭제하지 못했습니다. 잠시 후 다시 시도해주세요.",
    };
  }

  revalidatePath("/billable-metrics");
  return { status: "done" };
}
