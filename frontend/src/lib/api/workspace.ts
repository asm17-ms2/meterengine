import "server-only";

import { AGGREGATE_REQUEST, serverFetch } from "@/lib/api/client";
import { config } from "@/lib/config";

export type InvoicePaymentResponse = {
  invoice_id: string; status: string; mode: string; amount: number;
  canceled_amount: number; attempt_count: number; last_failure_code: string | null;
  next_retry_at: string | null; completed_at: string | null;
};
export type DashboardInsightsResponse = {
  month: string; calculated_at: string;
  monthly_amounts: { month: string; finalized_total_amount: number; collected_amount: number; collected_supply_amount: number; outstanding_supply_amount: number; draft_supply_amount: number }[];
  daily_event_counts: { date: string; event_count: number }[];
  recent_event_counts: { date: string; event_count: number }[];
};

export function getDashboardInsights(month: string) {
  return serverFetch<DashboardInsightsResponse>(config.apiBaseUrl, "/v1/dashboard/insights", { month }, AGGREGATE_REQUEST);
}

export function workspaceFetch<T>(path: string, params?: Record<string, string | undefined>, options?: { timeoutMs?: number; shareInFlight?: boolean }) {
  return serverFetch<T>(config.apiBaseUrl, path, params, options);
}
