import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type DraftInvoiceResponse = {
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
};

export type DraftInvoiceLine = {
  billable_metric_code: string;
  target_property: string | null;
  quantity: number;
  unit_price: number;
  amount: number;
};

export function countDraftInvoiceLines(customers: DraftInvoiceCustomer[]): number {
  return customers.reduce((sum, customer) => sum + customer.lines.length, 0);
}

export async function previewDraftInvoice(
  month: string,
  devState: DevState,
): Promise<Result<DraftInvoiceResponse>> {
  if (devState === "empty") {
    return {
      ok: true,
      data: {
        month,
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
  return serverFetch<DraftInvoiceResponse>(config.apiBaseUrl, "/v1/invoices/draft", { month });
}
