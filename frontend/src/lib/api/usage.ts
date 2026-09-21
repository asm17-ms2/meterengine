import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type ListBillableMetricUsagesResponse = {
  month: string;
  billable_metric_usages: BillableMetricUsageResponse[];
};

export type BillableMetricUsageResponse = {
  code: string;
  name: string;
  event_type: string;
  aggregation: string;
  target_property: string | null;
  customers: BillableMetricUsageCustomer[];
};

export type BillableMetricUsageCustomer = {
  customer_id: string;
  customer_name: string;
  quantity: number;
};

export type CustomerGroup = {
  customerId: string;
  customerName: string;
  billableMetricLines: BillableMetricLine[];
};

export type BillableMetricLine = {
  label: string;
  quantity: number;
};

export function toCustomerGroups(usage: ListBillableMetricUsagesResponse): CustomerGroup[] {
  const customerGroupByCustomerId = new Map<string, CustomerGroup>();

  for (const billableMetricUsage of usage.billable_metric_usages) {
    const label = billableMetricUsage.target_property
      ? `${billableMetricUsage.code} (${billableMetricUsage.target_property})`
      : billableMetricUsage.code;

    for (const customer of billableMetricUsage.customers) {
      let group = customerGroupByCustomerId.get(customer.customer_id);
      if (!group) {
        group = {
          customerId: customer.customer_id,
          customerName: customer.customer_name,
          billableMetricLines: [],
        };
        customerGroupByCustomerId.set(customer.customer_id, group);
      }
      group.billableMetricLines.push({ label, quantity: customer.quantity });
    }
  }

  return [...customerGroupByCustomerId.values()];
}

export function countBillableMetricLines(groups: CustomerGroup[]): number {
  return groups.reduce((sum, group) => sum + group.billableMetricLines.length, 0);
}

export async function aggregateBillableMetricUsages(
  month: string,
  devState: DevState,
): Promise<Result<ListBillableMetricUsagesResponse>> {
  if (devState === "empty") {
    return { ok: true, data: { month, billable_metric_usages: [] } };
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
  return serverFetch<ListBillableMetricUsagesResponse>(config.apiBaseUrl, "/v1/usage", { month });
}
