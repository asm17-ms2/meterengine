import "server-only";
import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";

export type PriceRateTier = { up_to: number | null; unit_price: number };
export type PriceRateResponse = {
  id: string; billable_metric_code: string; dimension_values: Record<string, string | number | boolean>;
  unit_price: number | null; customer_id: string | null; effective_from: string;
  effective_until: string | null; archived_at?: string | null; archived_from?: string | null; model: "per_unit" | "graduated" | "volume"; tiers: PriceRateTier[] | null;
};
export type PricePlanResponse = { id: string; customer_id: string | null; effective_from: string; effective_until: string | null; base_fee: number; minimum_amount: number; archived_at?: string | null; archived_from?: string | null };
export type ListPriceRatesResponse = { price_rates: PriceRateResponse[] };
export type ListPricePlansResponse = { price_plans: PricePlanResponse[] };
export type BillableMetricPricePriceRate = Omit<PriceRateResponse, "billable_metric_code">;
export type ListBillableMetricPricesResponse = { billable_metric_prices: { billable_metric_code: string; dimension_properties: string[]; price_rates: BillableMetricPricePriceRate[] }[] };

export function listPriceRates(code: string, customerId?: string, status = "active"): Promise<Result<ListPriceRatesResponse>> {
  return serverFetch(config.apiBaseUrl, `/v1/billable-metrics/${encodeURIComponent(code)}/price-rates`, { customer_id: customerId, status });
}
export function listPricePlans(customerId?: string, status = "active"): Promise<Result<ListPricePlansResponse>> {
  return serverFetch(config.apiBaseUrl, "/v1/price-plans", { customer_id: customerId, status });
}
export function listBillableMetricPrices(month: string, customerId?: string): Promise<Result<ListBillableMetricPricesResponse>> {
  return serverFetch(config.apiBaseUrl, "/v1/billable-metric-prices", { month, customer_id: customerId });
}
