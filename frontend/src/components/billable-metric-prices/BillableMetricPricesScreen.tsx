"use client";

import { useTenantScope } from "@/components/auth/TenantAccess";

import { Button, Flex } from "antd";
import { useCallback, useState } from "react";

import type { BillableMetricPriceRowView } from "@/app/(console)/billable-metric-prices/state";
import { BillableMetricPricesTable } from "@/components/billable-metric-prices/BillableMetricPricesTable";
import { PriceRateFormDialog } from "@/components/billable-metric-prices/PriceRateFormDialog";
import { EmptyState } from "@/components/screen/EmptyState";
import { FilterBar } from "@/components/screen/FilterBar";
import { LinkButton } from "@/components/screen/LinkButton";
import { ListControls } from "@/components/screen/ListControls";
import { ListPagination } from "@/components/screen/ListPagination";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { ScreenNote } from "@/components/screen/ScreenNote";
import { SearchInput } from "@/components/screen/SearchInput";
import { useSearchText } from "@/components/screen/use-search-text";
import type { ClientSortSelection } from "@/components/table/table-sort";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

const PRICE_CAPABILITIES = CAPABILITIES.listBillableMetricPrices;

function filterBySearchText(
  rows: BillableMetricPriceRowView[],
  searchText: string,
): BillableMetricPriceRowView[] {
  const normalized = searchText.trim().toLowerCase();
  if (normalized === "") return rows;
  return rows.filter(
    (row) => row.name.toLowerCase().includes(normalized) || row.code.toLowerCase().includes(normalized),
  );
}

export function BillableMetricPricesScreen({
  query,
  rows,
  total,
}: {
  query: ListQuery;
  rows: BillableMetricPriceRowView[];
  total?: number;
}) {
  const canManage = useTenantScope("admin");
  const capabilities = PRICE_CAPABILITIES;
  const search = useSearchText(query, capabilities);
  const searchText = search.text;
  const [sortSelection, setSortSelection] = useState<ClientSortSelection | undefined>(undefined);
  const [registering, setRegistering] = useState<BillableMetricPriceRowView | null>(null);

  const closeForm = useCallback(() => setRegistering(null), []);

  const visibleRows = capabilities.q ? rows : filterBySearchText(rows, searchText);
  const noRateCount = visibleRows.filter((row) => row.status === "no_rate").length;
  const trimmedSearchText = searchText.trim();
  const hasNoMetrics = rows.length === 0 && (!capabilities.q || query.q === "");

  return (
    <>
      <ScreenHeader title="가격">
        <Flex gap="medium" wrap>
          <span>
            미터 <b>{visibleRows.length}</b>개
          </span>
          <span>
            단가 없음 <b>{noRateCount}</b>개
          </span>
        </Flex>
      </ScreenHeader>

      <ScreenCard>
        <FilterBar>
          <ListControls
            query={query}
            capabilities={capabilities}
            search={
              <SearchInput
                label="미터 이름이나 코드 검색"
                value={searchText}
                onChange={search.setText}
                onBlur={search.flush}
              />
            }
          />
        </FilterBar>

        {hasNoMetrics ? (
          <EmptyState
            title="등록된 미터가 없습니다"
            body="가격은 미터마다 붙습니다. 미터 화면에서 미터를 먼저 등록하세요."
            action={<LinkButton href="/billable-metrics">미터 화면으로</LinkButton>}
          />
        ) : visibleRows.length === 0 ? (
          <EmptyState
            title="검색 결과가 없습니다"
            body={`"${trimmedSearchText}"에 맞는 미터가 없습니다. 등록된 미터는 ${rows.length}개입니다.`}
            action={<Button onClick={search.clear}>검색어 지우기</Button>}
          />
        ) : (
          <>
            <BillableMetricPricesTable
              query={query}
              capabilities={capabilities}
              rows={visibleRows}
              onRegisterRate={setRegistering}
              clientSortSelection={{ selection: sortSelection, onSelectionChange: setSortSelection }}
            />
            <ListPagination query={query} capabilities={capabilities} total={total} />
            <ScreenNote>
              정렬: 코드 오름차순, 조합 오름차순. 차원 키가 없는 미터의 단가는 조합 칸이 비고 속성
              조건 없이 적용됩니다. 단가가 없는 조합은 청구 예정액에서 빠집니다.
            </ScreenNote>
          </>
        )}
      </ScreenCard>

      {registering && canManage ? (
        <PriceRateFormDialog
          key={registering.code}
          billableMetricPrice={registering}
          onClose={closeForm}
        />
      ) : null}
    </>
  );
}
