"use client";

import { useCallback, useState } from "react";

import type { BillableMetricRowView } from "@/app/(console)/billable-metrics/state";
import { BillableMetricDeleteDialog } from "@/components/billable-metrics/BillableMetricDeleteDialog";
import { BillableMetricFormDialog } from "@/components/billable-metrics/BillableMetricFormDialog";
import { BillableMetricsTable } from "@/components/billable-metrics/BillableMetricsTable";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function BillableMetricsScreen({ rows }: { rows: BillableMetricRowView[] }) {
  const [search, setSearch] = useState("");
  const [form, setForm] = useState<{ billableMetric: BillableMetricRowView | null } | null>(
    null,
  );
  const [deleting, setDeleting] = useState<BillableMetricRowView | null>(null);

  const closeForm = useCallback(() => setForm(null), []);
  const closeDelete = useCallback(() => setDeleting(null), []);

  const query = search.trim().toLowerCase();
  const visible =
    query === ""
      ? rows
      : rows.filter((row) => row.name.toLowerCase().includes(query));

  return (
    <>
      <ScreenHeader title="미터">
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <span>
            총 <b>{visible.length}</b>개
          </span>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setForm({ billableMetric: null })}
          >
            미터 등록
          </button>
        </div>
      </ScreenHeader>

      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="미터 이름 검색"
          placeholder="미터 이름 검색"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </FilterBar>

      {rows.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">등록된 미터가 없습니다</div>
          <p className="empty-state__body">
            이 도입사에 등록된 미터가 없습니다. 미터 등록으로 첫 미터를
            추가하세요.
          </p>
          <button
            type="button"
            className="btn btn-secondary"
            style={{ marginTop: 4 }}
            onClick={() => setForm({ billableMetric: null })}
          >
            미터 등록
          </button>
        </div>
      ) : visible.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">검색 결과가 없습니다</div>
          <p className="empty-state__body">
            &quot;{search.trim()}&quot;에 맞는 미터가 없습니다. 등록된 미터는{" "}
            {rows.length}개입니다.
          </p>
          <button
            type="button"
            className="btn btn-secondary"
            style={{ marginTop: 4 }}
            onClick={() => setSearch("")}
          >
            검색어 지우기
          </button>
        </div>
      ) : (
        <>
          <BillableMetricsTable
            rows={visible}
            onEdit={(row) => setForm({ billableMetric: row })}
            onDelete={(row) => setDeleting(row)}
          />
          <div className="screen-footer">
            <span className="screen-note">정렬: 코드 오름차순</span>
          </div>
        </>
      )}

      {form ? (
        <BillableMetricFormDialog
          key={form.billableMetric?.code ?? "new"}
          billableMetric={form.billableMetric}
          onClose={closeForm}
        />
      ) : null}

      {deleting ? (
        <BillableMetricDeleteDialog billableMetric={deleting} onClose={closeDelete} />
      ) : null}
    </>
  );
}
