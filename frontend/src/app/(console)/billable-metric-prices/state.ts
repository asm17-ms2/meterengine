export type BillableMetricPriceStatus = "no_policy" | "no_rate" | "billing";

export type BillableMetricPriceRowView = {
  code: string;
  name: string;
  dimensionProperties: string[] | null;
  unitPrice: string | null;
  status: BillableMetricPriceStatus;
};

export type PricePolicyField = "dimension_properties";

export type PricePolicyFormState =
  | { status: "idle" }
  | { status: "invalid"; fieldErrors: Partial<Record<PricePolicyField, string>> }
  | { status: "failed"; message: string }
  | { status: "done" };

export const PRICE_POLICY_FORM_IDLE: PricePolicyFormState = { status: "idle" };
