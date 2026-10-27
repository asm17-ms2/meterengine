"use client";

import { useTenantScope } from "@/components/auth/TenantAccess";

import { Button } from "antd";
import { useCallback, useState } from "react";

import type { BillableMetricRowView } from "@/app/(console)/billable-metrics/state";
import { BillableMetricDeleteDialog } from "@/components/billable-metrics/BillableMetricDeleteDialog";
import { BillableMetricFormDialog } from "@/components/billable-metrics/BillableMetricFormDialog";
import { BillableMetricsTable } from "@/components/billable-metrics/BillableMetricsTable";
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

function filterByName(rows: BillableMetricRowView[], searchText: string): BillableMetricRowView[] {
  const needle = searchText.toLowerCase();
  if (needle === "") return rows;
  return rows.filter((row) => row.name.toLowerCase().includes(needle));
}

export function BillableMetricsScreen({
  rows,
  total,
  query,
  capabilities,
}: {
  rows: BillableMetricRowView[];
  total?: number;
  query: ListQuery;
  capabilities: ListCapabilities;
}) {
  const canManage = useTenantScope("admin");
  const search = useSearchText(query, capabilities);
  const searchText = search.text;
  const [sortSelection, setSortSelection] = useState<ClientSortSelection | undefined>(undefined);
  const [form, setForm] = useState<{ billableMetric: BillableMetricRowView | null } | null>(
    null,
  );
  const [deleting, setDeleting] = useState<BillableMetricRowView | null>(null);

  const openCreate = useCallback(() => setForm({ billableMetric: null }), []);
  const closeForm = useCallback(() => setForm(null), []);
  const closeDelete = useCallback(() => setDeleting(null), []);

  const appliedSearchText = capabilities.q ? query.q.trim() : searchText.trim();
  const visibleRows = capabilities.q ? rows : filterByName(rows, appliedSearchText);
  const hasNoBillableMetrics =
    rows.length === 0 && (!capabilities.q || appliedSearchText === "");

  return (
    <>
      <ScreenHeader
        title="미터"
        extra={
          canManage ? <Button type="primary" onClick={openCreate}>
            미터 등록
          </Button> : undefined
        }
      >
        총 <b>{visibleRows.length}</b>개
      </ScreenHeader>

      <ScreenCard>
        <FilterBar>
          <ListControls
            query={query}
            capabilities={capabilities}
            search={
              <SearchInput
                label="미터 이름 검색"
                value={searchText}
                onChange={search.setText}
                onBlur={search.flush}
              />
            }
          />
        </FilterBar>

        {hasNoBillableMetrics ? (
          <EmptyState
            title={query.status === "archived" ? "보관된 미터가 없습니다" : query.status === "all" ? "등록된 미터가 없습니다" : "사용 중인 미터가 없습니다"}
            body="미터를 새로 등록하거나 관리 상태를 바꿔 보관한 미터를 확인하세요."
            action={canManage ? <Button onClick={openCreate}>미터 등록</Button> : undefined}
          />
        ) : visibleRows.length === 0 ? (
          <EmptyState
            title="검색 결과가 없습니다"
            body={`"${appliedSearchText}"에 맞는 미터가 없습니다. 등록된 미터는 ${rows.length}개입니다.`}
            action={<Button onClick={search.clear}>검색어 지우기</Button>}
          />
        ) : (
          <>
            <BillableMetricsTable
              rows={visibleRows}
              query={query}
              capabilities={capabilities}
              onEdit={(row) => setForm({ billableMetric: row })}
              onDelete={(row) => setDeleting(row)}
              clientSortSelection={{ selection: sortSelection, onSelectionChange: setSortSelection }}
            />
            <ListPagination query={query} capabilities={capabilities} total={total} />
            <ScreenNote>정렬: 코드 오름차순</ScreenNote>
          </>
        )}
      </ScreenCard>

      {form && canManage ? (
        <BillableMetricFormDialog
          key={form.billableMetric?.code ?? "new"}
          billableMetric={form.billableMetric}
          onClose={closeForm}
        />
      ) : null}

      {deleting && canManage ? (
        <BillableMetricDeleteDialog billableMetric={deleting} onClose={closeDelete} />
      ) : null}
    </>
  );
}
