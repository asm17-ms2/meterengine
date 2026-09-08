import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function BillableMetricsFrame({ children }: { children: React.ReactNode }) {
  return (
    <>
      <ScreenHeader title="미터" />
      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="미터 이름 검색"
          placeholder="미터 이름 검색"
          disabled
        />
      </FilterBar>
      {children}
    </>
  );
}
