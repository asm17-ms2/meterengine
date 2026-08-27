"use client";

import { useCallback, useState } from "react";

import type { MetricRowView } from "@/app/(console)/metrics/state";
import { MetricDeleteDialog } from "@/components/metrics/MetricDeleteDialog";
import { MetricFormDialog } from "@/components/metrics/MetricFormDialog";
import { MetricTable } from "@/components/metrics/MetricTable";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function MetricsScreen({ rows }: { rows: MetricRowView[] }) {
  const [search, setSearch] = useState("");
  const [form, setForm] = useState<{ metric: MetricRowView | null } | null>(
    null,
  );
  const [deleting, setDeleting] = useState<MetricRowView | null>(null);

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
            onClick={() => setForm({ metric: null })}
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
            onClick={() => setForm({ metric: null })}
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
          <MetricTable
            rows={visible}
            onEdit={(row) => setForm({ metric: row })}
            onDelete={(row) => setDeleting(row)}
          />
          <div className="screen-footer">
            <span className="screen-note">정렬: 코드 오름차순</span>
          </div>
        </>
      )}

      {form ? (
        <MetricFormDialog metric={form.metric} onClose={closeForm} />
      ) : null}

      {deleting ? (
        <MetricDeleteDialog metric={deleting} onClose={closeDelete} />
      ) : null}
    </>
  );
}
