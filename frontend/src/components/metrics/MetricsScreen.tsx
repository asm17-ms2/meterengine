"use client";

import { useCallback, useState } from "react";

import type { MetricRowView } from "@/app/(console)/metrics/state";
import { MetricFormDialog } from "@/components/metrics/MetricFormDialog";
import { MetricTable } from "@/components/metrics/MetricTable";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

export function MetricsScreen() {
  const [rows, setRows] = useState<MetricRowView[]>([]);
  const [formOpen, setFormOpen] = useState(false);

  const closeForm = useCallback(() => setFormOpen(false), []);
  const addRow = useCallback((metric: MetricRowView) => {
    setRows((prev) =>
      [...prev, metric].sort((a, b) => a.code.localeCompare(b.code)),
    );
  }, []);

  return (
    <>
      <ScreenHeader title="미터">
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <span>
            총 <b>{rows.length}</b>개
          </span>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setFormOpen(true)}
          >
            미터 등록
          </button>
        </div>
      </ScreenHeader>

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
            onClick={() => setFormOpen(true)}
          >
            미터 등록
          </button>
        </div>
      ) : (
        <>
          <MetricTable rows={rows} />
          <div className="screen-footer">
            <span className="screen-note">정렬: 코드 오름차순</span>
          </div>
        </>
      )}

      {formOpen ? (
        <MetricFormDialog onClose={closeForm} onRegistered={addRow} />
      ) : null}
    </>
  );
}
