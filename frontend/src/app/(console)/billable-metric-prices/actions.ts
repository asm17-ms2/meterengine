"use server";

import { revalidatePath } from "next/cache";

import {
  DIMENSION_PROPERTIES_FIELD,
  readDimensionProperties,
} from "@/app/(console)/billable-metric-prices/dimension-properties";
import type { PricePolicyFormState } from "@/app/(console)/billable-metric-prices/state";
import { createPricePolicy } from "@/lib/api/billable-metric-prices";
import { type ApiError, toDisplayMessage } from "@/lib/api/client";

const STALE_ROW_CODES = ["price_policy_already_exists", "billable_metric_not_found"];

function isDimensionPropertiesField(field: string): boolean {
  return (
    field === DIMENSION_PROPERTIES_FIELD || field.startsWith(`${DIMENSION_PROPERTIES_FIELD}[`)
  );
}

function toFailureState(error: ApiError): PricePolicyFormState {
  const fieldError = error.errors?.find((candidate) =>
    isDimensionPropertiesField(candidate.field),
  );
  if (fieldError) {
    return { status: "invalid", fieldErrors: { dimension_properties: fieldError.message } };
  }
  if (STALE_ROW_CODES.includes(error.code)) {
    revalidatePath("/billable-metric-prices");
  }
  return { status: "failed", message: toDisplayMessage(error) };
}

export async function createPricePolicyAction(
  _prev: PricePolicyFormState,
  formData: FormData,
): Promise<PricePolicyFormState> {
  const code = formData.get("code");
  if (typeof code !== "string" || code === "") {
    return { status: "failed", message: "미터를 특정하지 못했습니다." };
  }

  const read = readDimensionProperties(formData);
  if (!read.ok) {
    return { status: "invalid", fieldErrors: { dimension_properties: read.message } };
  }

  const result = await createPricePolicy(code, { dimension_properties: read.keys });
  if (!result.ok) return toFailureState(result.error);

  revalidatePath("/billable-metric-prices");
  return { status: "done" };
}
