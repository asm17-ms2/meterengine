"use client";

import Link from "next/link";
import { useState } from "react";

import type { BillableMetricPriceRowView } from "@/app/(console)/billable-metric-prices/state";
import { BillableMetricPricesTable } from "@/components/billable-metric-prices/BillableMetricPricesTable";
import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function BillableMetricPricesScreen({ rows }: { rows: BillableMetricPriceRowView[] }) {
  const [search, setSearch] = useState("");

  const query = search.trim().toLowerCase();
  const visibleRows =
    query === ""
      ? rows
      : rows.filter(
          (row) =>
            row.name.toLowerCase().includes(query) || row.code.toLowerCase().includes(query),
        );
  const noPolicyCount = visibleRows.filter((row) => row.status === "no_policy").length;
  const noRateCount = visibleRows.filter((row) => row.status === "no_rate").length;

  return (
    <>
      <ScreenHeader title="가격">
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <span>
            미터 <b>{visibleRows.length}</b>개
          </span>
          <span>
            정책 없음 <b>{noPolicyCount}</b>개
          </span>
          <span>
            단가 없음 <b>{noRateCount}</b>개
          </span>
        </div>
      </ScreenHeader>

      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="미터 이름이나 코드 검색"
          placeholder="미터 이름이나 코드 검색"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </FilterBar>

      {rows.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">등록된 미터가 없습니다</div>
          <p className="empty-state__body">
            가격은 미터마다 붙습니다. 미터 화면에서 미터를 먼저 등록하세요.
          </p>
          <Link className="btn btn-secondary" href="/billable-metrics" style={{ marginTop: 4 }}>
            미터 화면으로
          </Link>
        </div>
      ) : visibleRows.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__title">검색 결과가 없습니다</div>
          <p className="empty-state__body">
            &quot;{search.trim()}&quot;에 맞는 미터가 없습니다. 등록된 미터는 {rows.length}개입니다.
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
          <BillableMetricPricesTable rows={visibleRows} />
          <div className="screen-footer">
            <span className="screen-note">
              정렬: 코드 오름차순. 단가는 속성 조건 없는 기본 단가만 보입니다.
            </span>
          </div>
        </>
      )}
    </>
  );
}
