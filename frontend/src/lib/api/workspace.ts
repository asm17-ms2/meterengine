import "server-only";

import { serverFetch } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DraftInvoiceResponse } from "@/lib/api/billing";

export type InvoicePaymentResponse = {
  invoice_id: string; status: string; mode: string; amount: number;
  canceled_amount: number; attempt_count: number; last_failure_code: string | null;
  next_retry_at: string | null; completed_at: string | null;
};
export type DashboardResponse = {
  month: string; calculated_at: string; customer_count: number; billable_metric_count: number;
  active_api_key_count: number; event_count: number; supplier_configured: boolean;
  payment_configured: boolean; payment_mode: string; draft_supply_amount: number;
  finalized_total_amount: number; collected_amount: number; canceled_amount: number;
  outstanding_amount: number; failed_payment_count: number; unpriced_usage_count: number;
  negative_usage_count: number; invoice_payments: (InvoicePaymentResponse & { customer_id: string; customer_name: string; month: string })[]; draft_invoice: DraftInvoiceResponse;
  unresolved_invoice_payments: (InvoicePaymentResponse & { customer_id: string; customer_name: string; month: string })[];
};

export function getDashboard(month: string) {
  return serverFetch<DashboardResponse>(config.apiBaseUrl, "/v1/dashboard", { month });
}

export function workspaceFetch<T>(path: string, params?: Record<string, string | undefined>) {
  return serverFetch<T>(config.apiBaseUrl, path, params);
}
