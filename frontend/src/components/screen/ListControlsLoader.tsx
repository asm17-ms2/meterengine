import { ResourceSearchControls, type SearchResource } from "@/components/screen/ResourceSearchControls";
import type { CustomerOption } from "@/components/screen/CustomerFilter";
import { ListControls, type ListControlsMonth } from "@/components/screen/ListControls";
import type { ListBillableMetricsResponse } from "@/lib/api/billable-metrics";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import type { ListCustomersResponse } from "@/lib/api/customers";
import type { ListQuery } from "@/lib/list-query";
import { searchOptions } from "@/lib/api/search";
import { serverFetch } from "@/lib/api/client";
import { config } from "@/lib/config";

async function loadCustomerOptions(
  customers?: Promise<Result<ListCustomersResponse>>,
  selectedId?: string,
): Promise<CustomerOption[]> {
  void customers;
  const result = await searchOptions("customers");
  if (!result.ok) return [];
  const options = result.data.items.map(option => ({ value: option.value, label: option.label + (option.archived ? " (보관됨)" : "") }));
  if (selectedId && !options.some(option => option.value === selectedId)) {
    const selected = await serverFetch<{ id: string; name: string; archived_at?: string | null }>(config.apiBaseUrl, `/v1/customers/${encodeURIComponent(selectedId)}`);
    if (selected.ok) options.unshift({ value: selected.data.id, label: selected.data.name + (selected.data.archived_at ? " (보관됨)" : "") });
  }
  return options;
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
  resource,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  month?: ListControlsMonth;
  customers?: Promise<Result<ListCustomersResponse>>;
  metrics?: Promise<Result<ListBillableMetricsResponse>>;
  search?: React.ReactNode;
  resource?: SearchResource;
}) {
  const [customerOptions, eventTypes] = await Promise.all([
    capabilities.customer_id ? loadCustomerOptions(customers, query.customerId) : Promise.resolve([]),
    loadEventTypes(metrics),
  ]);

  if (resource) return <ResourceSearchControls query={query} capabilities={capabilities} resource={resource} month={month} customers={customerOptions} />;

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
