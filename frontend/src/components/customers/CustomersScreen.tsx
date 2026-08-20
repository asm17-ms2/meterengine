"use client";

import { useCallback, useState } from "react";

import { CustomerDeleteDialog } from "@/components/customers/CustomerDeleteDialog";
import { CustomerFormDialog } from "@/components/customers/CustomerFormDialog";
import {
  CustomerTable,
  type CustomerRowView,
} from "@/components/customers/CustomerTable";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

/**
 * 고객 화면의 상호작용 전부 (MS2-154).
 *
 * 다른 세 화면과 달리 제목 줄과 필터 행까지 클라이언트가 그린다. 검색어가 화면
 * 안의 상태인데 제목 옆 "총 N명"이 그 결과를 세야 해서, 셋이 한 상태를 봐야
 * 하기 때문이다.
 *
 * 검색을 쿼리스트링(?q=)에 두지 않은 이유: 백엔드에 검색 파라미터가 없다.
 * 목록 응답이 이 도입사의 고객 전부라 거를 대상이 이미 화면에 다 와 있고,
 * 글자를 칠 때마다 서버를 왕복할 일이 아니다.
 */
export function CustomersScreen({ rows }: { rows: CustomerRowView[] }) {
  const [search, setSearch] = useState("");
  const [form, setForm] = useState<{ customer: CustomerRowView | null } | null>(
    null,
  );
  const [deleting, setDeleting] = useState<CustomerRowView | null>(null);

  const closeForm = useCallback(() => setForm(null), []);
  const closeDelete = useCallback(() => setDeleting(null), []);

  const query = search.trim().toLowerCase();
  const visible =
    query === ""
      ? rows
      : rows.filter((row) => row.name.toLowerCase().includes(query));

  return (
    <>
      <ScreenHeader title="고객">
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <span>
            총 <b>{visible.length}</b>명
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
        // 공통 EmptyState를 쓰지 않는다. 그쪽 버튼은 '필터 초기화' 링크인데,
        // 이 화면에는 초기화할 필터가 없고 다음 행동이 등록이라서다.
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
      ) : visible.length === 0 ? (
        // 검색이 다 걸러낸 경우. 위의 빈 상태와 할 말이 다르다. 고객은 있고
        // 지금 친 글자에 맞는 것이 없을 뿐이라, 등록이 아니라 검색어를 지우는
        // 것이 다음 행동이다. EmptyState는 초기화가 링크라 여기 쓸 수 없다.
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
          <CustomerTable
            rows={visible}
            onEdit={(row) => setForm({ customer: row })}
            onDelete={(row) => setDeleting(row)}
          />
          <div className="screen-footer">
            {/*
              응답이 정렬 기준을 에코하지 않아서 화면에 고정한다. 백엔드는 고객명
              오름차순으로 주고, 동명이 있으면 customer_id가 두 번째 키다.
              페이지 나누기는 없다 - 이 도입사의 전부가 응답의 정의다.
            */}
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
