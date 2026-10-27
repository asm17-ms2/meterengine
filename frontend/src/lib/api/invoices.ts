import "server-only";

import type { DimensionValues } from "@/lib/api/billable-metric-prices";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import {
  MAX_PAGE_SIZE,
  toServerParams,
  type ListQuery,
  type PaginationFields,
} from "@/lib/list-query";

export type ListInvoicesResponse = PaginationFields & {
  invoices: InvoiceResponse[];
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
  return serverFetch<ListInvoicesResponse>(
    config.apiBaseUrl,
    "/v1/invoices",
    toServerParams(query, CAPABILITIES.listInvoices),
  );
}

export async function listAllInvoices(
  query: ListQuery,
  devState: DevState,
): Promise<Result<ListInvoicesResponse>> {
  if (!CAPABILITIES.listInvoices.page) {
    return listInvoices(query, devState);
  }
  const invoices: InvoiceResponse[] = [];
  for (let page = 0; ; page += 1) {
    const result = await listInvoices({ ...query, page, size: MAX_PAGE_SIZE }, devState);
    if (!result.ok) {
      return result;
    }
    invoices.push(...result.data.invoices);
    const { total } = result.data;
    if (total === undefined || invoices.length >= total || result.data.invoices.length === 0) {
      return { ok: true, data: { invoices, total: invoices.length } };
    }
  }
}
