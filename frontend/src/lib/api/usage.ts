import "server-only";
import type { SearchPage } from "@/lib/api/search";

import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import { formatTargetPropertyTooltip } from "@/lib/format";
import { metricCalculationFormula, type MetricCalculation } from "@/lib/billable-metric-calculation";
import { toServerParams, type MonthlyListQuery, type PaginationFields } from "@/lib/list-query";

export type ListBillableMetricUsagesResponse = PaginationFields & {
  month: string;
  summary?: { month: string; customer_count: number; metric_count: number; line_count: number; nonzero_customer_count: number };
  billable_metric_usages: BillableMetricUsageResponse[];
};

export type BillableMetricUsageResponse = MetricCalculation & {
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
  dimension_usages: BillableMetricUsageDimensionUsage[];
  quantity: number;
  raw_quantity?: number;
  raw_quantity_numerator?: number;
  raw_quantity_denominator?: number;
};

export type BillableMetricUsageDimensionUsage = {
  dimension_values: Record<string, string | number | boolean | null>;
  quantity: number;
  raw_quantity?: number;
  raw_quantity_numerator?: number;
  raw_quantity_denominator?: number;
};

export type CustomerGroup = {
  customerId: string;
  customerName: string;
  billableMetricLines: BillableMetricLine[];
};

export type BillableMetricLine = {
  label: string;
  labelTooltip?: string;
  dimensionValues: BillableMetricUsageDimensionUsage["dimension_values"];
  quantity: number;
  rawQuantity?: number;
  overallQuantity: number;
  overallRawQuantity?: number;
};

const NO_USAGE_DIMENSION_USAGE: BillableMetricUsageDimensionUsage = {
  dimension_values: {},
  quantity: 0,
};

function toBillableMetricLines(
  label: string,
  labelTooltip: string | undefined,
  customer: BillableMetricUsageCustomer,
): BillableMetricLine[] {
  const dimensionUsages = customer.dimension_usages;
  const rows = dimensionUsages.length === 0 ? [NO_USAGE_DIMENSION_USAGE] : dimensionUsages;
  return rows.map((dimensionUsage) => ({
    label,
    labelTooltip,
    dimensionValues: dimensionUsage.dimension_values,
    quantity: dimensionUsage.quantity,
    rawQuantity: dimensionUsage.raw_quantity,
    overallQuantity: customer.quantity,
    overallRawQuantity: customer.raw_quantity,
  }));
}

export function toCustomerGroups(usage: ListBillableMetricUsagesResponse): CustomerGroup[] {
  const customerGroupByCustomerId = new Map<string, CustomerGroup>();

  for (const billableMetricUsage of usage.billable_metric_usages) {
    const label = billableMetricUsage.code;
    const labelTooltip = [`미터명: ${billableMetricUsage.name}`, formatTargetPropertyTooltip(billableMetricUsage.target_property), `계산: ${metricCalculationFormula(billableMetricUsage)}`].filter(Boolean).join(" / ");

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
      group.billableMetricLines.push(...toBillableMetricLines(label, labelTooltip, customer));
    }
  }

  return [...customerGroupByCustomerId.values()];
}

export function countBillableMetricLines(groups: CustomerGroup[]): number {
  return groups.reduce((sum, group) => sum + group.billableMetricLines.length, 0);
}

export async function aggregateBillableMetricUsages(
  query: MonthlyListQuery,
  devState: DevState,
): Promise<Result<ListBillableMetricUsagesResponse>> {
  if (devState === "empty") {
    return { ok: true, data: { month: query.month, billable_metric_usages: [] } };
  }
  if (devState === "error") {
    return {
      ok: false,
      error: {
        status: 503,
        code: "dev_forced",
        message:
          "개발 모드에서 강제한 에러 상태입니다. 주소의 state 파라미터를 지우면 사라집니다.",
      },
    };
  }
  const result = await serverFetch<SearchPage<{ customer_id: string; customer_name: string; metrics: BillableMetricUsageResponse[] }, NonNullable<ListBillableMetricUsagesResponse["summary"]>>>(
    config.apiBaseUrl, "/v1/usage/search", toServerParams(query, CAPABILITIES.aggregateBillableMetricUsages),
  );
  if (!result.ok) return result;
  const metrics = new Map<string, BillableMetricUsageResponse>();
  for (const customer of result.data.items) for (const metric of customer.metrics) {
    const existing = metrics.get(metric.code);
    if (existing) existing.customers.push(...metric.customers);
    else metrics.set(metric.code, { ...metric, customers: [...metric.customers] });
  }
  return { ok: true, data: { ...result.data, page: result.data.page - 1, month: result.data.summary.month, billable_metric_usages: [...metrics.values()] } };

}
