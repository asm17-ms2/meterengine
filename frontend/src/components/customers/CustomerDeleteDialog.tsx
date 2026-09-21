"use client";

import { useActionState, useEffect } from "react";

import { deleteCustomerAction } from "@/app/(console)/customers/actions";
import { CUSTOMER_DELETE_IDLE } from "@/app/(console)/customers/state";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { Dialog } from "@/components/screen/Dialog";

export function CustomerDeleteDialog({
  customer,
  onClose,
}: {
  customer: CustomerRowView;
  onClose: () => void;
}) {
  const [state, formAction, isPending] = useActionState(
    deleteCustomerAction,
    CUSTOMER_DELETE_IDLE,
  );

  useEffect(() => {
    if (state.status === "done") onClose();
  }, [state, onClose]);

  if (state.status === "rejected") {
    return (
      <Notice
        title="삭제할 수 없습니다"
        body={`${state.name}로 수집된 이벤트가 있습니다. 이벤트가 있는 고객은 삭제할 수 없습니다.`}
        note="서버가 이벤트 존재 여부를 확인하고 거절했습니다 (409)."
        onClose={onClose}
      />
    );
  }

  if (state.status === "gone") {
    return (
      <Notice
        title="이미 삭제된 고객입니다"
        body={`${state.name}는 이 목록을 연 뒤에 삭제됐습니다. 목록에서 사라집니다.`}
        note="서버에 그 고객이 없습니다 (404)."
        onClose={onClose}
      />
    );
  }

  return (
    <Dialog
      labelledBy="customer-delete-title"
      onClose={isPending ? undefined : onClose}
      action={formAction}
    >
      <div className="dialog-title" id="customer-delete-title">
        고객을 삭제할까요?
      </div>

      <input type="hidden" name="id" value={customer.id} />
      <input type="hidden" name="name" value={customer.name} />

      <div style={{ fontSize: 15 }}>{customer.name}</div>
      <div
        className="detail-grid__mono"
        style={{ fontSize: 12.5, color: "var(--text-55)" }}
      >
        {customer.id}
      </div>
      <p className="dialog-body" style={{ margin: 0 }}>
        수집된 이벤트가 한 건이라도 있으면 삭제할 수 없습니다. 저장할 때 서버가
        확인합니다.
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
          disabled={isPending}
          onClick={onClose}
        >
          취소
        </button>
        <button type="submit" className="btn btn-primary" disabled={isPending}>
          {isPending ? "삭제 중..." : "삭제"}
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
      labelledBy="customer-delete-notice-title"
      onClose={onClose}
      style={{ borderLeft: "4px solid var(--color-accent)" }}
    >
      <div
        className="dialog-title"
        id="customer-delete-notice-title"
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
