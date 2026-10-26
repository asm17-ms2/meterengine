import { CustomersFrame } from "@/components/customers/CustomersFrame";
import { CustomersScreen } from "@/components/customers/CustomersScreen";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { ErrorState } from "@/components/screen/ErrorState";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import type { ListCustomersResponse } from "@/lib/api/customers";
import { formatKstDate } from "@/lib/format";
import type { ListQuery } from "@/lib/list-query";

export async function CustomersSection({
  customers,
  query,
}: {
  customers: Promise<Result<ListCustomersResponse>>;
  query: ListQuery;
}) {
  const result = await customers;

  if (!result.ok) {
    return (
      <CustomersFrame query={query} capabilities={CAPABILITIES.listCustomers}>
        <ErrorState
          title="고객 목록을 불러오지 못했습니다"
          error={result.error}
        />
      </CustomersFrame>
    );
  }

  const rows: CustomerRowView[] = result.data.customers.map((customer) => ({
    id: customer.id,
    name: customer.name,
    billingEmail: customer.billing_email,
    createdAt: formatKstDate(customer.created_at),
    createdAtIso: customer.created_at,
  }));

  return (
    <CustomersScreen
      rows={rows}
      total={result.data.total}
      query={query}
      capabilities={CAPABILITIES.listCustomers}
    />
  );
}
