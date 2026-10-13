import type { DimensionValues } from "@/lib/api/billable-metric-prices";

export type BillableMetricPriceStatus = "no_rate" | "billing";

export type PriceRateRowView = {
  dimensionValues: DimensionValues;
  combination: string;
  unitPrice: string;
};

export type BillableMetricPriceRowView = {
  code: string;
  name: string;
  dimensionProperties: string[];
  priceRates: PriceRateRowView[];
  status: BillableMetricPriceStatus;
};

export type PriceRateDeleteState = { status: "idle" } | { status: "failed"; message: string };

export const PRICE_RATE_DELETE_IDLE: PriceRateDeleteState = { status: "idle" };

export type PriceRateFormState = PriceRateDeleteState | { status: "done" };

export const PRICE_RATE_FORM_IDLE: PriceRateFormState = { status: "idle" };
