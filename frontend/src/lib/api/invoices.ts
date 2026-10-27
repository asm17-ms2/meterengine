import "server-only";
import type { SearchPage } from "@/lib/api/search";
import type { InvoicePaymentResponse } from "@/lib/api/workspace";

import type { DimensionValues } from "@/lib/api/billable-metric-prices";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import { ALL_MONTHS } from "@/lib/month";
import {
  toServerParams,
  type ListQuery,
  type PaginationFields,
} from "@/lib/list-query";

export type ListInvoicesResponse = PaginationFields & {
  invoices: InvoiceResponse[];
  payments?: Record<string, InvoicePaymentResponse>;
  summary?: { mode: string; invoice_count: number; supply_amount: number; tax_amount: number; total_amount: number; canceled_amount: number; has_pending: boolean; outstanding_amount: number | null; status_counts: Record<string, number> };
};

export type InvoiceResponse = {
  id: string;
  invoice_number?: string;
  customer_id: string;
  customer_name: string;
  month: string;
  finalized_at: string;
  supply_amount: number;
  tax_amount: number;
  total_amount: number;
  lines: InvoiceLineResponse[];
};

export type InvoiceLineResponse = {
  billable_metric_code: string | null;
  target_property: string | null;
  dimension_values: DimensionValues;
  quantity: number;
  quantity_numerator?: number | null;
  quantity_denominator?: number | null;
  unit_price: number;
  amount: number;
  line_type?: "per_unit" | "volume" | "graduated_tier" | "base_fee" | "minimum_adjustment";
  tier_index?: number;
};

export type FinalizeInvoicesResponse = {
  month: string;
  finalized_count: number;
  invoices: InvoiceResponse[];
  supply_amount: number;
  tax_amount: number;
  total_amount: number;
};

export async function finalizeInvoices(month: string): Promise<Result<FinalizeInvoicesResponse>> {
  return serverSend<FinalizeInvoicesResponse>(config.apiBaseUrl, `/v1/invoices?month=${month}`, {
    method: "POST",
  });
}

export async function listInvoices(
  query: ListQuery,
  devState: DevState,
): Promise<Result<ListInvoicesResponse>> {
  if (devState === "empty") {
    return { ok: true, data: { invoices: [] } };
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
  const result = await serverFetch<SearchPage<{ invoice: InvoiceResponse; payment: InvoicePaymentResponse }, NonNullable<ListInvoicesResponse["summary"]>>>(
    config.apiBaseUrl, "/v1/invoices/search", toServerParams(query, CAPABILITIES.listInvoices),
  );
  return result.ok ? { ok: true, data: { ...result.data, page: result.data.page - 1, invoices: result.data.items.map(item => item.invoice), payments: Object.fromEntries(result.data.items.map(item => [item.invoice.id, item.payment])) } } : result;
}

export async function listAllInvoices(
  query: ListQuery,
  devState: DevState,
): Promise<Result<ListInvoicesResponse>> {
  if (devState === "empty" || devState === "error") return listInvoices(query, devState);
  const params: Record<string, string> = {};
  if (query.month && query.month !== ALL_MONTHS) params.month = query.month;
  if (query.customerId) params.customer_id = query.customerId;
  const result = await serverFetch<ListInvoicesResponse>(config.apiBaseUrl, "/v1/invoices", params);
  return result.ok ? { ok: true, data: { ...result.data, total: result.data.invoices.length } } : result;
}
