import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function CustomersFrame({ children }: { children: React.ReactNode }) {
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
