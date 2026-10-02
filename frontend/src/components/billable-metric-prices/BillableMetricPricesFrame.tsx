import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function BillableMetricPricesFrame({ children }: { children: React.ReactNode }) {
  return (
    <>
      <ScreenHeader title="가격" />
      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="미터 이름이나 코드 검색"
          placeholder="미터 이름이나 코드 검색"
          disabled
        />
      </FilterBar>
      {children}
    </>
  );
}
