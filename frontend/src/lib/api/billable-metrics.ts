import "server-only";

import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type BillableMetricResponse = {
  code: string;
  name: string;
  event_type: string;
  aggregation: string;
  target_property: string;
};

export type ListBillableMetricsResponse = {
  billable_metrics: BillableMetricResponse[];
};

export type CreateBillableMetricRequest = BillableMetricResponse;

export type UpdateBillableMetricRequest = Omit<CreateBillableMetricRequest, "code">;

export async function listBillableMetrics(
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
        title: "개발 모드에서 강제한 에러 상태입니다",
        detail: "사이드바의 표 상태 스위치를 정상으로 되돌리면 사라집니다.",
      },
    };
  }

  return serverFetch<ListBillableMetricsResponse>(
    config.apiBaseUrl,
    "/v1/billable-metrics",
  );
}

export async function createBillableMetric(
  request: CreateBillableMetricRequest,
): Promise<Result<BillableMetricResponse>> {
  return serverSend<BillableMetricResponse>(
    config.apiBaseUrl,
    "/v1/billable-metrics",
    { method: "POST", body: request },
  );
}

export async function updateBillableMetric(
  code: string,
  request: UpdateBillableMetricRequest,
): Promise<Result<BillableMetricResponse>> {
  return serverSend<BillableMetricResponse>(
    config.apiBaseUrl,
    `/v1/billable-metrics/${encodeURIComponent(code)}`,
    { method: "PUT", body: request },
  );
}

export async function deleteBillableMetric(code: string): Promise<Result<void>> {
  return serverSend<void>(
    config.apiBaseUrl,
    `/v1/billable-metrics/${encodeURIComponent(code)}`,
    { method: "DELETE" },
  );
}
