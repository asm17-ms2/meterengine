import "server-only";

import { serverFetch } from "@/lib/api/client";
import { config } from "@/lib/config";

export type InvoicePaymentResponse = {
  invoice_id: string; status: string; mode: string; amount: number;
  canceled_amount: number; attempt_count: number; last_failure_code: string | null;
  next_retry_at: string | null; completed_at: string | null;
};


export function workspaceFetch<T>(path: string, params?: Record<string, string | undefined>) {
  return serverFetch<T>(config.apiBaseUrl, path, params);
}
