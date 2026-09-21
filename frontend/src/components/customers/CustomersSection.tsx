import { CustomersFrame } from "@/components/customers/CustomersFrame";
import { CustomersScreen } from "@/components/customers/CustomersScreen";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import type { ListCustomersResponse } from "@/lib/api/customers";
import { formatKstDate } from "@/lib/format";

export async function CustomersSection({
  customers,
}: {
  customers: Promise<Result<ListCustomersResponse>>;
}) {
  const result = await customers;

  if (!result.ok) {
    return (
      <CustomersFrame>
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
    createdAt: formatKstDate(customer.created_at),
  }));

  return <CustomersScreen rows={rows} />;
}
