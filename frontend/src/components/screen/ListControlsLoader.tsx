import type { CustomerOption } from "@/components/screen/CustomerFilter";
import { ListControls, type ListControlsMonth } from "@/components/screen/ListControls";
import type { ListBillableMetricsResponse } from "@/lib/api/billable-metrics";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import type { ListCustomersResponse } from "@/lib/api/customers";
import type { ListQuery } from "@/lib/list-query";

async function loadCustomerOptions(
  customers?: Promise<Result<ListCustomersResponse>>,
): Promise<CustomerOption[]> {
  if (!customers) return [];
  const result = await customers.catch(() => null);
  if (!result?.ok) return [];
  return result.data.customers.map((customer) => ({
    value: customer.id,
    label: customer.archived_at ? `${customer.name} (보관됨)` : customer.name,
  }));
}

async function loadEventTypes(
  metrics?: Promise<Result<ListBillableMetricsResponse>>,
): Promise<string[]> {
  if (!metrics) return [];
  const result = await metrics.catch(() => null);
  if (!result?.ok) return [];
  const eventTypes = new Set(result.data.billable_metrics.map((billableMetric) => billableMetric.event_type));
  return [...eventTypes].sort((left, right) => left.localeCompare(right));
}

export async function ListControlsLoader({
  query,
  capabilities,
  month,
  customers,
  metrics,
  search,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  month?: ListControlsMonth;
  customers?: Promise<Result<ListCustomersResponse>>;
  metrics?: Promise<Result<ListBillableMetricsResponse>>;
  search?: React.ReactNode;
}) {
  const [customerOptions, eventTypes] = await Promise.all([
    loadCustomerOptions(customers),
    loadEventTypes(metrics),
  ]);

  return (
    <ListControls
      query={query}
      capabilities={capabilities}
      month={month}
      customers={customerOptions}
      eventTypes={eventTypes}
      search={search}
    />
  );
}
