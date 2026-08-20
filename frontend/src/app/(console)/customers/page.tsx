import { Suspense } from "react";

import {
  CustomersLoading,
  CustomersSection,
} from "@/components/customers/CustomersSection";
import { loadCustomers } from "@/lib/api/customers";
import { readDevState } from "@/lib/dev-state";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

/**
 * 고객 관리 (MS2-154).
 *
 * 다른 세 화면보다 페이지가 얇다. 조회 조건이 없어서다 - 기간도 페이지도 없이
 * 이 도입사의 고객 전부를 한 번에 받는다. 제목 줄과 필터 행은 목록을 읽어야
 * 채워지는 값(총 N명)을 쓰므로 아래쪽 컴포넌트가 그린다.
 */
export default async function CustomersPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const devState = readDevState(params.state);

  // await하지 않고 넘긴다. 'loading'은 로더를 부르지 않고 스켈레톤으로 단락한다.
  const customers = devState === "loading" ? null : loadCustomers(devState);

  if (!customers) return <CustomersLoading />;

  return (
    <Suspense fallback={<CustomersLoading />}>
      <CustomersSection customers={customers} />
    </Suspense>
  );
}
