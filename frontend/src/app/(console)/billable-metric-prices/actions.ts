"use server";

import { revalidatePath } from "next/cache";

import type { PriceRateDeleteState, PriceRateFormState } from "@/app/(console)/billable-metric-prices/state";
import { createPriceRate, deletePriceRate, type DimensionValues } from "@/lib/api/billable-metric-prices";
import { toDisplayMessage } from "@/lib/api/client";

const PATH = "/billable-metric-prices";

function readText(formData: FormData, field: string): string {
  const raw = formData.get(field);
  return typeof raw === "string" ? raw.trim() : "";
}

function readDimensionValues(formData: FormData): DimensionValues {
  const values = formData.getAll("dimension_value");
  return Object.fromEntries(
    formData.getAll("dimension_key").map((key, index) => [String(key), String(values[index] ?? "").trim()]),
  );
}

export async function createPriceRateAction(
  _prev: PriceRateFormState,
  formData: FormData,
): Promise<PriceRateFormState> {
  const code = readText(formData, "code");
  const dimensionValues = readDimensionValues(formData);
  const unitPriceText = readText(formData, "unit_price");
  if (code === "") return { status: "failed", message: "미터를 특정하지 못했습니다." };
  if (Object.values(dimensionValues).includes("")) {
    return { status: "failed", message: "차원 키마다 값을 입력하세요." };
  }
  if (unitPriceText === "") return { status: "failed", message: "단가를 입력하세요." };

  const result = await createPriceRate(code, {
    dimension_values: dimensionValues,
    unit_price: Number(unitPriceText),
  });
  if (!result.ok) return { status: "failed", message: toDisplayMessage(result.error) };

  revalidatePath(PATH);
  return { status: "done" };
}

export async function deletePriceRateAction(
  _prev: PriceRateDeleteState,
  formData: FormData,
): Promise<PriceRateDeleteState> {
  const code = readText(formData, "code");
  const dimensionValues = readText(formData, "dimension_values");
  if (code === "" || dimensionValues === "") {
    return { status: "failed", message: "단가를 특정하지 못했습니다." };
  }

  const result = await deletePriceRate(code, JSON.parse(dimensionValues) as DimensionValues);
  if (!result.ok && result.error.code !== "price_rate_not_found") {
    return { status: "failed", message: toDisplayMessage(result.error) };
  }

  revalidatePath(PATH);
  return { status: "idle" };
}
