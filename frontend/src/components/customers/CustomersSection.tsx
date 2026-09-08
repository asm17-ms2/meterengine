import { CustomersFrame } from "@/components/customers/CustomersFrame";
import { CustomersScreen } from "@/components/customers/CustomersScreen";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import type { ListCustomersResponse } from "@/lib/api/customers";
import { formatKstDate } from "@/lib/format";

/**
 * 프라미스를 await하는 서버 컴포넌트. 페이지가 <Suspense>로 감싼다.
 *
 * 다른 화면의 Section과 달리 성공 경로에서 제목 줄과 필터 행까지 넘긴다.
 * 검색어가 화면 안의 상태이고 제목 옆 "총 N명"이 그 결과를 세야 해서, 그 셋을
 * 갈라 두면 상태를 위로 끌어올릴 자리가 없다 (CustomersScreen 주석 참조).
 */
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
