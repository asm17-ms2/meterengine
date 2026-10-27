import "server-only";
import type { SearchPage } from "@/lib/api/search";

import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import type { MetricCalculation } from "@/lib/billable-metric-calculation";
import { toServerParams, type ListQuery, type PaginationFields } from "@/lib/list-query";

export type BillableMetricResponse = MetricCalculation & {
  code: string;
  version: number;
  name: string;
  event_type: string;
  target_property: string | null;
  dimension_properties: string[];
  archived_at?: string | null;
  active_from?: string | null;
  inactive_from?: string | null;
};

export type ListBillableMetricsResponse = PaginationFields & {
  summary?: Record<string, unknown>;
  billable_metrics: BillableMetricResponse[];
};

export type CreateBillableMetricRequest = Omit<
  BillableMetricResponse,
  "dimension_properties" | "version" | "archived_at" | "active_from" | "inactive_from"
> & {
  dimension_properties?: string[];
};

export type UpdateBillableMetricRequest = Omit<CreateBillableMetricRequest, "code">;

export async function listBillableMetrics(
  query: ListQuery,
  devState: DevState,
): Promise<Result<ListBillableMetricsResponse>> {
  if (devState === "empty") {
    return { ok: true, data: { billable_metrics: [] } };
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

  const result = await serverFetch<SearchPage<BillableMetricResponse>>(
    config.apiBaseUrl, "/v1/billable-metrics/search", toServerParams(query, CAPABILITIES.listBillableMetrics),
  );
  return result.ok ? { ok: true, data: { ...result.data, billable_metrics: result.data.items, page: result.data.page - 1 } } : result;
}

export async function createBillableMetric(
  request: CreateBillableMetricRequest,
  idempotencyKey?: string,
): Promise<Result<BillableMetricResponse>> {
  return serverSend<BillableMetricResponse>(
    config.apiBaseUrl,
    "/v1/billable-metrics",
    { method: "POST", body: request, idempotencyKey },
  );
}

export async function updateBillableMetric(
  code: string,
  request: UpdateBillableMetricRequest,
  version?: number,
): Promise<Result<BillableMetricResponse>> {
  return serverSend<BillableMetricResponse>(
    config.apiBaseUrl,
    `/v1/billable-metrics/${encodeURIComponent(code)}`,
    { method: "PATCH", body: request, ifMatch: version === undefined ? undefined : `"${version}"` },
  );
}

export async function deleteBillableMetric(code: string): Promise<Result<void>> {
  return serverSend<void>(
    config.apiBaseUrl,
    `/v1/billable-metrics/${encodeURIComponent(code)}`,
    { method: "DELETE" },
  );
}
