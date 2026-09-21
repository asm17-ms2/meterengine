"use client";

import { useCallback, useState } from "react";

import { CustomerDeleteDialog } from "@/components/customers/CustomerDeleteDialog";
import { CustomerFormDialog } from "@/components/customers/CustomerFormDialog";
import {
  CustomersTable,
  type CustomerRowView,
} from "@/components/customers/CustomersTable";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function CustomersScreen({ rows }: { rows: CustomerRowView[] }) {
  const [search, setSearch] = useState("");
  const [form, setForm] = useState<{ customer: CustomerRowView | null } | null>(
    null,
  );
  const [deleting, setDeleting] = useState<CustomerRowView | null>(null);

  const closeForm = useCallback(() => setForm(null), []);
  const closeDelete = useCallback(() => setDeleting(null), []);

  const query = search.trim().toLowerCase();
  const visibleRows =
    query === ""
      ? rows
      : rows.filter((row) => row.name.toLowerCase().includes(query));

  return (
    <>
      <ScreenHeader title="고객">
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <span>
            총 <b>{visibleRows.length}</b>명
          </span>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setForm({ customer: null })}
          >
            고객 등록
          </button>
        </div>
      </ScreenHeader>

      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="고객 이름 검색"
          placeholder="고객 이름 검색"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </FilterBar>

      {rows.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">등록된 고객이 없습니다</div>
          <p className="empty-state__body">
            이 도입사에 등록된 고객이 없습니다. 고객 등록으로 첫 고객을
            추가하세요.
          </p>
          <button
            type="button"
            className="btn btn-secondary"
            style={{ marginTop: 4 }}
            onClick={() => setForm({ customer: null })}
          >
            고객 등록
          </button>
        </div>
      ) : visibleRows.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">검색 결과가 없습니다</div>
          <p className="empty-state__body">
            &quot;{search.trim()}&quot;에 맞는 고객이 없습니다. 등록된 고객은{" "}
            {rows.length}명입니다.
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
          <CustomersTable
            rows={visibleRows}
            onEdit={(row) => setForm({ customer: row })}
            onDelete={(row) => setDeleting(row)}
          />
          <div className="screen-footer">
            <span className="screen-note">정렬: 고객명 오름차순</span>
          </div>
        </>
      )}

      {form ? (
        <CustomerFormDialog customer={form.customer} onClose={closeForm} />
      ) : null}

      {deleting ? (
        <CustomerDeleteDialog customer={deleting} onClose={closeDelete} />
      ) : null}
    </>
  );
}
