"use server";

import { revalidatePath } from "next/cache";

import {
  BILLABLE_METRIC_AGGREGATION,
  type BillableMetricDeleteState,
  type BillableMetricField,
  type BillableMetricFormState,
} from "@/app/(console)/billable-metrics/state";
import { type ApiError, toDisplayMessage } from "@/lib/api/client";
import { createBillableMetric, deleteBillableMetric, updateBillableMetric } from "@/lib/api/billable-metrics";

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
  target_property: "집계 대상 속성을 입력하세요. sum 집계는 필수입니다",
};

function readField(formData: FormData, field: BillableMetricField): string {
  const raw = formData.get(field);
  return typeof raw === "string" ? raw.trim() : "";
}

function isBillableMetricField(field: string): field is BillableMetricField {
  return (BILLABLE_METRIC_FIELDS as readonly string[]).includes(field);
}

function toServerFieldErrors(
  error: ApiError,
): Partial<Record<BillableMetricField, string>> {
  const fieldErrors: Partial<Record<BillableMetricField, string>> = {};
  for (const fieldError of error.errors ?? []) {
    if (isBillableMetricField(fieldError.field)) fieldErrors[fieldError.field] = fieldError.message;
  }
  return fieldErrors;
}

function toFailureState(error: ApiError): BillableMetricFormState {
  if (error.code === "billable_metric_already_exists") {
    return { status: "invalid", fieldErrors: { code: error.message } };
  }
  const fieldErrors = toServerFieldErrors(error);
  if (Object.keys(fieldErrors).length > 0) {
    return { status: "invalid", fieldErrors };
  }
  return { status: "failed", message: toDisplayMessage(error) };
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
    aggregation: BILLABLE_METRIC_AGGREGATION,
    target_property: values.target_property,
  });
  if (!result.ok) return toFailureState(result.error);

  revalidatePath("/billable-metrics");
  return { status: "done" };
}

const UPDATE_FIELDS: readonly BillableMetricField[] = [
  "name",
  "event_type",
  "target_property",
];

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
    aggregation: BILLABLE_METRIC_AGGREGATION,
    target_property: values.target_property,
  });
  if (!result.ok) return toFailureState(result.error);

  revalidatePath("/billable-metrics");
  return { status: "done" };
}

export async function deleteBillableMetricAction(
  _prev: BillableMetricDeleteState,
  formData: FormData,
): Promise<BillableMetricDeleteState> {
  const code = formData.get("code");
  const name = formData.get("name");
  const billableMetricName = typeof name === "string" ? name : "";
  if (typeof code !== "string" || code === "") {
    return { status: "failed", message: "미터를 특정하지 못했습니다." };
  }

  const result = await deleteBillableMetric(code);
  if (!result.ok) {
    if (result.error.code === "billable_metric_has_events") {
      return { status: "rejected", reason: "events", name: billableMetricName };
    }
    if (result.error.code === "billable_metric_has_price_policy") {
      return { status: "rejected", reason: "policy", name: billableMetricName };
    }
    if (result.error.code === "billable_metric_not_found") {
      revalidatePath("/billable-metrics");
      return { status: "gone", name: billableMetricName };
    }
    return { status: "failed", message: toDisplayMessage(result.error) };
  }

  revalidatePath("/billable-metrics");
  return { status: "done" };
}
