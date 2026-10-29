"use server";

import { revalidatePath } from "next/cache";
import { requireTenantSession } from "@/lib/auth/session";
import { hasScope } from "@/lib/auth/permissions";

import {
  billableMetricFailureMessage,
  type BillableMetricDeleteState,
  type BillableMetricField,
  type BillableMetricFormState,
} from "@/app/(console)/billable-metrics/state";
import { type ApiError, toDisplayMessage } from "@/lib/api/client";
import { createBillableMetric, deleteBillableMetric, updateBillableMetric } from "@/lib/api/billable-metrics";
import { isMetricAggregation, isMetricRoundingMode, type MetricCalculation } from "@/lib/billable-metric-calculation";

type RequiredBillableMetricField = "code" | "name" | "event_type";

const BILLABLE_METRIC_FIELDS: readonly BillableMetricField[] = [
  "code",
  "name",
  "event_type",
  "aggregation",
  "target_property",
  "percentile",
  "unit_size",
  "rounding_mode",
  "rounding_precision",
  "dimension_properties",
];

const CREATE_REQUIRED_FIELDS: readonly RequiredBillableMetricField[] = [
  "code",
  "name",
  "event_type",
];

const UPDATE_REQUIRED_FIELDS: readonly RequiredBillableMetricField[] = [
  "name",
  "event_type",
];

const REQUIRED_MESSAGES: Record<RequiredBillableMetricField, string> = {
  code: "코드를 입력하세요",
  name: "이름을 입력하세요",
  event_type: "이벤트 타입을 입력하세요",
};

const DIMENSION_PROPERTIES_SEPARATOR = ",";

function readField(formData: FormData, field: BillableMetricField): string {
  const raw = formData.get(field);
  return typeof raw === "string" ? raw.trim() : "";
}

function readDimensionProperties(formData: FormData): string[] {
  return readField(formData, "dimension_properties")
    .split(DIMENSION_PROPERTIES_SEPARATOR)
    .map((key) => key.trim())
    .filter((key) => key !== "");
}

function readRequiredFields(
  formData: FormData,
  fields: readonly RequiredBillableMetricField[],
): {
  values: Record<RequiredBillableMetricField, string>;
  fieldErrors: Partial<Record<BillableMetricField, string>>;
} {
  const fieldErrors: Partial<Record<BillableMetricField, string>> = {};
  const values = {} as Record<RequiredBillableMetricField, string>;
  for (const field of fields) {
    values[field] = readField(formData, field);
    if (values[field] === "") fieldErrors[field] = REQUIRED_MESSAGES[field];
  }
  return { values, fieldErrors };
}

function readCalculation(formData: FormData): {
  values: MetricCalculation & { target_property: string | null };
  fieldErrors: Partial<Record<BillableMetricField, string>>;
} {
  const fieldErrors: Partial<Record<BillableMetricField, string>> = {};
  const aggregation = readField(formData, "aggregation") || "sum";
  if (!isMetricAggregation(aggregation)) fieldErrors.aggregation = "지원하는 집계 함수를 선택하세요";
  const targetProperty = aggregation === "count" ? null : readField(formData, "target_property");
  if (aggregation !== "count" && !targetProperty) fieldErrors.target_property = "집계 대상 속성을 입력하세요";
  const percentileText = readField(formData, "percentile");
  const percentile = aggregation === "percentile" ? Number(percentileText) : null;
  if (aggregation === "percentile" && (!percentileText || !Number.isFinite(percentile) || percentile! < 0 || percentile! > 100)) {
    fieldErrors.percentile = "백분위는 0부터 100 사이의 숫자로 입력하세요";
  }
  const unitSize = Number(readField(formData, "unit_size") || "1");
  if (!Number.isFinite(unitSize) || unitSize <= 0) fieldErrors.unit_size = "과금 단위 크기는 0보다 커야 합니다";
  const roundingMode = readField(formData, "rounding_mode") || "none";
  if (!isMetricRoundingMode(roundingMode)) fieldErrors.rounding_mode = "반올림 방식을 선택하세요";
  const roundingPrecision = Number(readField(formData, "rounding_precision") || "0");
  if (!Number.isInteger(roundingPrecision) || roundingPrecision < 0 || roundingPrecision > 12) {
    fieldErrors.rounding_precision = "소수 자릿수는 0부터 12 사이의 정수로 입력하세요";
  }
  return {
    values: {
      aggregation,
      target_property: targetProperty,
      percentile,
      unit_size: unitSize,
      rounding_mode: isMetricRoundingMode(roundingMode) ? roundingMode : "none",
      rounding_precision: roundingMode === "none" ? 0 : roundingPrecision,
    },
    fieldErrors,
  };
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
  return { status: "failed", message: billableMetricFailureMessage(error.code, toDisplayMessage(error)) };
}

export async function createBillableMetricAction(
  _prev: BillableMetricFormState,
  formData: FormData,
): Promise<BillableMetricFormState> {
  const session = await requireTenantSession();
  if (!hasScope(session.scopes, "admin")) return { status: "failed", message: "이 작업을 수행할 관리 권한이 없습니다. 대표관리자에게 권한을 요청하세요." };
  const { values, fieldErrors } = readRequiredFields(formData, CREATE_REQUIRED_FIELDS);
  const calculation = readCalculation(formData);
  Object.assign(fieldErrors, calculation.fieldErrors);
  if (Object.keys(fieldErrors).length > 0) {
    return { status: "invalid", fieldErrors };
  }

  const result = await createBillableMetric({
    code: values.code,
    name: values.name,
    event_type: values.event_type,
    ...calculation.values,
    dimension_properties: readDimensionProperties(formData),
  });
  if (!result.ok) return toFailureState(result.error);

  revalidatePath("/billable-metrics");
  return { status: "done" };
}

export async function updateBillableMetricAction(
  _prev: BillableMetricFormState,
  formData: FormData,
): Promise<BillableMetricFormState> {
  const session = await requireTenantSession();
  if (!hasScope(session.scopes, "admin")) return { status: "failed", message: "이 작업을 수행할 관리 권한이 없습니다. 대표관리자에게 권한을 요청하세요." };
  const code = formData.get("code");
  if (typeof code !== "string" || code === "") {
    return { status: "failed", message: "미터를 특정하지 못했습니다." };
  }

  const { values, fieldErrors } = readRequiredFields(formData, UPDATE_REQUIRED_FIELDS);
  const calculation = readCalculation(formData);
  Object.assign(fieldErrors, calculation.fieldErrors);
  if (Object.keys(fieldErrors).length > 0) {
    return { status: "invalid", fieldErrors };
  }

  const result = await updateBillableMetric(code, {
    name: values.name,
    event_type: values.event_type,
    ...calculation.values,
    dimension_properties: readDimensionProperties(formData),
  });
  if (!result.ok) return toFailureState(result.error);

  revalidatePath("/billable-metrics");
  return { status: "done" };
}

export async function deleteBillableMetricAction(
  _prev: BillableMetricDeleteState,
  formData: FormData,
): Promise<BillableMetricDeleteState> {
  const session = await requireTenantSession();
  if (!hasScope(session.scopes, "admin")) return { status: "failed", message: "이 작업을 수행할 관리 권한이 없습니다. 대표관리자에게 권한을 요청하세요." };
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
    if (result.error.code === "billable_metric_has_price_rate") {
      return { status: "rejected", reason: "price_rates", name: billableMetricName };
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
