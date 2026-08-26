import "server-only";

import { serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";

export type Metric = {
  code: string;
  name: string;
  event_type: string;
  aggregation: string;
  target_property: string;
};

export async function registerMetric(metric: Metric): Promise<Result<Metric>> {
  return serverSend<Metric>(config.apiBaseUrl, "/v1/metrics", {
    method: "POST",
    body: metric,
  });
}
