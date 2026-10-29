import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";

export type SearchPage<T, S = Record<string, unknown>> = {
  items: T[];
  total: number;
  total_unfiltered: number;
  page: number;
  size: number;
  summary: S;
};

export type QueryOption = { value: string; label: string; archived: boolean };

export async function searchOptions(resource: "customers" | "billable-metrics", q = "", page = 1): Promise<Result<SearchPage<QueryOption>>> {
  return serverFetch(config.apiBaseUrl, `/v1/${resource}/options`, { q: q || undefined, status: "all", page: String(page), size: "200" });
}

export async function customerOptions(selectedId?: string): Promise<Result<QueryOption[]>> {
  const result = await searchOptions("customers");
  if (!result.ok) return result;
  const items = [...result.data.items];
  if (selectedId && !items.some(option => option.value === selectedId)) {
    const selected = await serverFetch<{ id: string; name: string; archived_at?: string | null }>(config.apiBaseUrl, `/v1/customers/${encodeURIComponent(selectedId)}`);
    if (selected.ok) items.unshift({ value: selected.data.id, label: selected.data.name, archived: Boolean(selected.data.archived_at) });
  }
  return { ok: true, data: items };
}

export async function dataMonths(resource: "events" | "usage" | "invoices" | "billable-metric-prices"): Promise<Result<{ months: string[] }>> {
  return serverFetch(config.apiBaseUrl, `/v1/${resource}/months`);
}
