"use client";

import { useActionState, useEffect } from "react";

import { deleteBillableMetricAction } from "@/app/(console)/billable-metrics/actions";
import {
  BILLABLE_METRIC_DELETE_IDLE,
  type BillableMetricRowView,
} from "@/app/(console)/billable-metrics/state";
import { Dialog } from "@/components/screen/Dialog";

export function BillableMetricDeleteDialog({
  billableMetric,
  onClose,
}: {
  billableMetric: BillableMetricRowView;
  onClose: () => void;
}) {
  const [state, formAction, pending] = useActionState(
    deleteBillableMetricAction,
    BILLABLE_METRIC_DELETE_IDLE,
  );

  useEffect(() => {
    if (state.status === "done") onClose();
  }, [state, onClose]);

  if (state.status === "rejected") {
    return state.reason === "events" ? (
      <Notice
        title="삭제할 수 없습니다"
        body={`${state.name} 미터가 잡는 이벤트가 있습니다. 이벤트는 청구 근거라 그 미터는 삭제할 수 없습니다.`}
        note="서버가 이벤트 존재를 확인하고 거절했습니다 (409)."
        onClose={onClose}
      />
    ) : (
      <Notice
        title="삭제할 수 없습니다"
        body={`${state.name} 미터를 가격 정책이 참조하고 있습니다. 정책이 참조하는 미터는 삭제할 수 없습니다.`}
        note="서버가 가격 정책 참조를 확인하고 거절했습니다 (409)."
        onClose={onClose}
      />
    );
  }

  if (state.status === "gone") {
    return (
      <Notice
        title="이미 삭제된 미터입니다"
        body={`${state.name}는 이 목록을 연 뒤에 삭제됐습니다. 목록에서 사라집니다.`}
        note="서버에 그 미터가 없습니다 (404)."
        onClose={onClose}
      />
    );
  }

  return (
    <Dialog
      labelledBy="metric-delete-title"
      onClose={pending ? undefined : onClose}
      action={formAction}
    >
      <div className="dialog-title" id="metric-delete-title">
        미터를 삭제할까요?
      </div>

      <input type="hidden" name="code" value={billableMetric.code} />
      <input type="hidden" name="name" value={billableMetric.name} />

      <div style={{ fontSize: 15 }}>{billableMetric.name}</div>
      <div
        className="detail-grid__mono"
        style={{ fontSize: 12.5, color: "var(--text-55)" }}
      >
        {billableMetric.code}
      </div>
      <p className="dialog-body" style={{ margin: 0 }}>
        삭제하면 이 미터의 사용량 집계와 청구 예정액 라인이 함께 사라집니다.
        삭제 가능 여부는 저장할 때 서버가 확인합니다.
      </p>

      {state.status === "failed" ? (
        <p
          style={{ margin: 0, fontSize: 12.5, color: "var(--color-accent-700)" }}
        >
          {state.message}
        </p>
      ) : null}

      <div className="dialog-actions">
        <button
          type="button"
          className="btn btn-secondary"
          disabled={pending}
          onClick={onClose}
        >
          취소
        </button>
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? "삭제 중..." : "삭제"}
        </button>
      </div>
    </Dialog>
  );
}

function Notice({
  title,
  body,
  note,
  onClose,
}: {
  title: string;
  body: string;
  note: string;
  onClose: () => void;
}) {
  return (
    <Dialog
      labelledBy="metric-delete-notice-title"
      onClose={onClose}
      style={{ borderLeft: "4px solid var(--color-accent)" }}
    >
      <div
        className="dialog-title"
        id="metric-delete-notice-title"
        style={{ color: "var(--color-accent-700)" }}
      >
        {title}
      </div>
      <p className="dialog-body" style={{ margin: 0, textWrap: "pretty" }}>
        {body}
      </p>
      <p style={{ margin: 0, fontSize: 12.5, color: "var(--text-55)" }}>
        {note}
      </p>
      <div className="dialog-actions">
        <button
          type="button"
          className="btn btn-secondary"
          autoFocus
          onClick={onClose}
        >
          확인
        </button>
      </div>
    </Dialog>
  );
}
