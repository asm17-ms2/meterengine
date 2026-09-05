import { CustomersScreen } from "@/components/customers/CustomersScreen";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { ErrorState } from "@/components/screen/ErrorState";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
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

  const rows: CustomerRowView[] = result.data.customers.map((entry) => ({
    id: entry.id,
    name: entry.name,
    createdAt: formatKstDate(entry.created_at),
  }));

  return <CustomersScreen rows={rows} />;
}

/** 표를 불러오는 동안. 제목과 필터 행은 이미 자리를 잡고 있다. */
export function CustomersLoading() {
  return (
    <CustomersFrame>
      <TableSkeleton />
    </CustomersFrame>
  );
}

/**
 * 목록이 없을 때의 제목 줄과 필터 행.
 *
 * 검색란을 비활성으로라도 두는 이유: 로딩과 에러에서 이 줄이 통째로 사라지면
 * 표가 그려질 때 화면이 위아래로 튄다. 걸러낼 목록이 없으니 쓸 수는 없다.
 * 제목 오른쪽의 "총 N명"과 등록 버튼은 뺀다 - 셀 수 있는 것이 없고, 목록도
 * 못 읽는 상태에서 등록만 열리면 저장한 결과를 확인할 수 없다.
 */
function CustomersFrame({ children }: { children: React.ReactNode }) {
  return (
    <>
      <ScreenHeader title="고객" />
      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="고객 이름 검색"
          placeholder="고객 이름 검색"
          disabled
        />
      </FilterBar>
      {children}
    </>
  );
}
