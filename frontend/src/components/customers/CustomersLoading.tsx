import { CustomersFrame } from "@/components/customers/CustomersFrame";
import { TableSkeleton } from "@/components/screen/TableSkeleton";

/** 표를 불러오는 동안. 제목과 필터 행은 이미 자리를 잡고 있다. */
export function CustomersLoading() {
  return (
    <CustomersFrame>
      <TableSkeleton />
    </CustomersFrame>
  );
}
