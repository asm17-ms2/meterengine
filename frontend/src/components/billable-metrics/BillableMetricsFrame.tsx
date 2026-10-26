import { FilterBar } from "@/components/screen/FilterBar";
import { ListControls } from "@/components/screen/ListControls";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { SearchInput } from "@/components/screen/SearchInput";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export function BillableMetricsFrame({
  query,
  capabilities,
  children,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  children: React.ReactNode;
}) {
  return (
    <>
      <ScreenHeader title="미터" />
      <ScreenCard>
        <FilterBar>
          <ListControls
            query={query}
            capabilities={capabilities}
            search={<SearchInput label="미터 이름 검색" value={query.q} disabled />}
          />
        </FilterBar>
        {children}
      </ScreenCard>
    </>
  );
}
