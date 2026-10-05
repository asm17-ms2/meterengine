import "server-only";

import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type BillableMetricPriceResponse = {
  billable_metric_code: string;
  dimension_properties: string[] | null;
  unit_price: number | null;
};

export type ListBillableMetricPricesResponse = {
  billable_metric_prices: BillableMetricPriceResponse[];
};

export type CreatePricePolicyRequest = {
  dimension_properties: string[];
};

export type PricePolicyResponse = {
  billable_metric_code: string;
  dimension_properties: string[];
};

export async function listBillableMetricPrices(
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
  );
}

export async function createPricePolicy(
  billableMetricCode: string,
  request: CreatePricePolicyRequest,
): Promise<Result<PricePolicyResponse>> {
  return serverSend<PricePolicyResponse>(
    config.apiBaseUrl,
    `/v1/billable-metrics/${encodeURIComponent(billableMetricCode)}/price-policy`,
    { method: "POST", body: request },
  );
}
