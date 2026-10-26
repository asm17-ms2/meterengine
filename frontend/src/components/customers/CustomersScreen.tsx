"use client";

import { Button } from "antd";
import { useCallback, useState } from "react";

import { CustomerDeleteDialog } from "@/components/customers/CustomerDeleteDialog";
import { CustomerFormDialog } from "@/components/customers/CustomerFormDialog";
import {
  CustomersTable,
  type CustomerRowView,
} from "@/components/customers/CustomersTable";
import { EmptyState } from "@/components/screen/EmptyState";
import { FilterBar } from "@/components/screen/FilterBar";
import { ListControls } from "@/components/screen/ListControls";
import { ListPagination } from "@/components/screen/ListPagination";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { ScreenNote } from "@/components/screen/ScreenNote";
import { SearchInput } from "@/components/screen/SearchInput";
import { useSearchText } from "@/components/screen/use-search-text";
import type { ClientSortSelection } from "@/components/table/table-sort";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

function filterByName(rows: CustomerRowView[], searchText: string): CustomerRowView[] {
  const needle = searchText.toLowerCase();
  if (needle === "") return rows;
  return rows.filter((row) => row.name.toLowerCase().includes(needle));
}

export function CustomersScreen({
  rows,
  total,
  query,
  capabilities,
}: {
  rows: CustomerRowView[];
  total?: number;
  query: ListQuery;
  capabilities: ListCapabilities;
}) {
  const search = useSearchText(query, capabilities);
  const searchText = search.text;
  const [sortSelection, setSortSelection] = useState<ClientSortSelection | undefined>(undefined);
  const [form, setForm] = useState<{ customer: CustomerRowView | null } | null>(
    null,
  );
  const [deleting, setDeleting] = useState<CustomerRowView | null>(null);

  const openCreate = useCallback(() => setForm({ customer: null }), []);
  const closeForm = useCallback(() => setForm(null), []);
  const closeDelete = useCallback(() => setDeleting(null), []);

  const appliedSearchText = capabilities.q ? query.q.trim() : searchText.trim();
  const visibleRows = capabilities.q ? rows : filterByName(rows, appliedSearchText);
  const hasNoCustomers = rows.length === 0 && (!capabilities.q || appliedSearchText === "");

  return (
    <>
      <ScreenHeader
        title="고객"
        extra={
          <Button type="primary" onClick={openCreate}>
            고객 등록
          </Button>
        }
      >
        총 <b>{visibleRows.length}</b>명
      </ScreenHeader>

      <ScreenCard>
        <FilterBar>
          <ListControls
            query={query}
            capabilities={capabilities}
            search={
              <SearchInput
                label="고객 이름 검색"
                value={searchText}
                onChange={search.setText}
                onBlur={search.flush}
              />
            }
          />
        </FilterBar>

        {hasNoCustomers ? (
          <EmptyState
            title="등록된 고객이 없습니다"
            body="이 도입사에 등록된 고객이 없습니다. 고객 등록으로 첫 고객을 추가하세요."
            action={<Button onClick={openCreate}>고객 등록</Button>}
          />
        ) : visibleRows.length === 0 ? (
          <EmptyState
            title="검색 결과가 없습니다"
            body={`"${appliedSearchText}"에 맞는 고객이 없습니다. 등록된 고객은 ${rows.length}명입니다.`}
            action={<Button onClick={search.clear}>검색어 지우기</Button>}
          />
        ) : (
          <>
            <CustomersTable
              rows={visibleRows}
              query={query}
              capabilities={capabilities}
              onEdit={(row) => setForm({ customer: row })}
              onDelete={(row) => setDeleting(row)}
              clientSortSelection={{ selection: sortSelection, onSelectionChange: setSortSelection }}
            />
            <ListPagination query={query} capabilities={capabilities} total={total} />
            <ScreenNote>정렬: 고객명 오름차순</ScreenNote>
          </>
        )}
      </ScreenCard>

      {form ? (
        <CustomerFormDialog customer={form.customer} onClose={closeForm} />
      ) : null}

      {deleting ? (
        <CustomerDeleteDialog customer={deleting} onClose={closeDelete} />
      ) : null}
    </>
  );
}
