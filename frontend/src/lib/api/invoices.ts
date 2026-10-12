import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type ListInvoicesResponse = {
  invoices: InvoiceResponse[];
};

export type InvoiceResponse = {
  id: string;
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
  billable_metric_code: string;
  target_property: string | null;
  quantity: number;
  unit_price: number;
  amount: number;
};

export async function listInvoices(
  month: string,
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
  return serverFetch<ListInvoicesResponse>(config.apiBaseUrl, "/v1/invoices", { month });
}
