import "server-only";

import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import { toServerParams, type ListQuery, type PaginationFields } from "@/lib/list-query";

export type DimensionValues = Record<string, string | number | boolean>;

export type BillableMetricPricePriceRate = {
  dimension_values: DimensionValues;
  unit_price: number;
};

export type BillableMetricPriceResponse = {
  billable_metric_code: string;
  dimension_properties: string[];
  price_rates: BillableMetricPricePriceRate[];
};

export type ListBillableMetricPricesResponse = PaginationFields & {
  billable_metric_prices: BillableMetricPriceResponse[];
};

export type CreatePriceRateRequest = BillableMetricPricePriceRate;

export type PriceRateResponse = BillableMetricPricePriceRate & { billable_metric_code: string };

export async function listBillableMetricPrices(
  query: ListQuery,
  devState: DevState,
): Promise<Result<ListBillableMetricPricesResponse>> {
  if (devState === "empty") {
    return { ok: true, data: { billable_metric_prices: [] } };
  }
  if (devState === "error") {
    return {
      ok: false,
      error: {
        status: 503,
        code: "dev_forced",
        message:
          "개발 모드에서 강제한 에러 상태입니다. 사이드바의 표 상태 스위치를 정상으로 되돌리면 사라집니다.",
      },
    };
  }

  return serverFetch<ListBillableMetricPricesResponse>(
    config.apiBaseUrl,
    "/v1/billable-metric-prices",
    toServerParams(query, CAPABILITIES.listBillableMetricPrices),
  );
}

export async function createPriceRate(
  billableMetricCode: string,
  request: CreatePriceRateRequest,
): Promise<Result<PriceRateResponse>> {
  return serverSend<PriceRateResponse>(
    config.apiBaseUrl,
    `/v1/billable-metrics/${encodeURIComponent(billableMetricCode)}/price-rates`,
    { method: "POST", body: request },
  );
}

export async function deletePriceRate(
  billableMetricCode: string,
  dimensionValues: DimensionValues,
): Promise<Result<void>> {
  const query = encodeURIComponent(JSON.stringify(dimensionValues));
  return serverSend<void>(
    config.apiBaseUrl,
    `/v1/billable-metrics/${encodeURIComponent(billableMetricCode)}/price-rates?dimension_values=${query}`,
    { method: "DELETE" },
  );
}
