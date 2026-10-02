export type BillableMetricPriceStatus = "no_policy" | "no_rate" | "billing";

export type BillableMetricPriceRowView = {
  code: string;
  name: string;
  dimensionProperties: string[] | null;
  unitPrice: string | null;
  status: BillableMetricPriceStatus;
};
