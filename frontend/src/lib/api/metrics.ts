import "server-only";

import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type Metric = {
  code: string;
  name: string;
  event_type: string;
  aggregation: string;
  target_property: string;
};

export type MetricList = {
  metrics: Metric[];
};

export async function loadMetrics(
  devState: DevState,
): Promise<Result<MetricList>> {
  if (devState === "empty") {
    return { ok: true, data: { metrics: [] } };
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

  return serverFetch<MetricList>(config.apiBaseUrl, "/v1/metrics");
}

export async function registerMetric(metric: Metric): Promise<Result<Metric>> {
  return serverSend<Metric>(config.apiBaseUrl, "/v1/metrics", {
    method: "POST",
    body: metric,
  });
}

export type MetricUpdate = Omit<Metric, "code">;

export async function updateMetric(
  code: string,
  metric: MetricUpdate,
): Promise<Result<Metric>> {
  return serverSend<Metric>(
    config.apiBaseUrl,
    `/v1/metrics/${encodeURIComponent(code)}`,
    { method: "PUT", body: metric },
  );
}

export async function deleteMetric(code: string): Promise<Result<void>> {
  return serverSend<void>(
    config.apiBaseUrl,
    `/v1/metrics/${encodeURIComponent(code)}`,
    { method: "DELETE" },
  );
}
