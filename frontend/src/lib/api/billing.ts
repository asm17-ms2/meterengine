import "server-only";

import type { DimensionValues } from "@/lib/api/billable-metric-prices";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { InvoiceResponse, ListInvoicesResponse } from "@/lib/api/invoices";
import type { DevState } from "@/lib/dev-state";
import { toServerParams, type MonthlyListQuery, type PaginationFields } from "@/lib/list-query";

export type DraftInvoiceResponse = PaginationFields & {
  month: string;
  calculated_at: string;
  total_amount: number;
  customers: DraftInvoiceCustomer[];
};

export type DraftInvoiceCustomer = {
  customer_id: string;
  customer_name: string;
  amount: number;
  lines: DraftInvoiceLine[];
  unpriced_usages: DraftInvoiceUnpricedUsage[];
  negative_usages?: DraftInvoiceUnpricedUsage[];
};

export type DraftInvoiceLine = {
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

export type DraftInvoiceUnpricedUsage = {
  billable_metric_code: string;
  target_property: string | null;
  dimension_values: DimensionValues;
  quantity: number;
};

export function countDraftInvoiceLines(customers: DraftInvoiceCustomer[]): number {
  return customers.reduce((sum, customer) => sum + customer.lines.length, 0);
}

export function hasUnpricedUsages(customers: DraftInvoiceCustomer[]): boolean {
  return customers.some((customer) => customer.unpriced_usages.length > 0);
}

export function listFinalizeTargets(customers: DraftInvoiceCustomer[]): DraftInvoiceCustomer[] {
  return customers.filter((customer) => customer.amount !== 0);
}

export function isFinalizedMonth(
  customers: DraftInvoiceCustomer[],
  invoices: InvoiceResponse[],
): boolean {
  return invoices.length > 0 && listFinalizeTargets(customers).length === 0;
}

export async function resolveIsFinalizedMonth(
  draftInvoice: Promise<Result<DraftInvoiceResponse>>,
  invoices: Promise<Result<ListInvoicesResponse>>,
): Promise<boolean> {
  const [draft, finalized] = await Promise.all([draftInvoice, invoices]);
  return (
    draft.ok && finalized.ok && isFinalizedMonth(draft.data.customers, finalized.data.invoices)
  );
}

export async function previewDraftInvoice(
  query: MonthlyListQuery,
  devState: DevState,
): Promise<Result<DraftInvoiceResponse>> {
  if (devState === "empty") {
    return {
      ok: true,
      data: {
        month: query.month,
        calculated_at: new Date().toISOString(),
        total_amount: 0,
        customers: [],
      },
    };
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
  return serverFetch<DraftInvoiceResponse>(
    config.apiBaseUrl,
    "/v1/invoices/draft",
    toServerParams(query, CAPABILITIES.previewDraftInvoice),
  );
}
