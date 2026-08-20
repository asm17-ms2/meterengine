"use client";

import { useActionState, useEffect } from "react";

import { deleteCustomerAction } from "@/app/(console)/customers/actions";
import { DELETE_IDLE } from "@/app/(console)/customers/state";
import type { CustomerRowView } from "@/components/customers/CustomerTable";
import { Dialog } from "@/components/screen/Dialog";

/**
 * 고객 삭제. 확인 -> (서버 판정) -> 결과 안내까지 한 창에서 넘어간다.
 *
 * 이벤트가 있는지 화면이 미리 알 수 없어서 이 모양이 된다. 목록 응답에 이벤트
 * 건수가 없고, 있더라도 확인 다이얼로그를 띄운 사이에 이벤트가 들어올 수 있다.
 * 판정은 저장할 때 서버가 하고, 화면은 그 답을 받아 창을 바꾼다.
 */
export function CustomerDeleteDialog({
  customer,
  onClose,
}: {
  customer: CustomerRowView;
  onClose: () => void;
}) {
  const [state, formAction, pending] = useActionState(
    deleteCustomerAction,
    DELETE_IDLE,
  );

  useEffect(() => {
    if (state.status === "done") onClose();
  }, [state, onClose]);

  // 서버가 거절했다 (409). 오류가 아니라 규칙이라 에러 블록이 아닌 안내다.
  //
  // 디자인은 여기에 이벤트 건수를 적었지만("...이벤트가 328건 있습니다") 그 값을
  // 채울 곳이 없다. 409 응답은 건수를 주지 않고, ProblemResponse의 detail은
  // 영어이고 개발자용이라 화면에 그대로 쓰지 말라고 계약에 적혀 있다.
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

  // 이미 없다 (404). 목록을 열어 둔 사이에 다른 곳에서 지워진 경우다.
  // 사용자가 원한 결과와 같은 상태이므로 "실패"라고 말하지 않는다.
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
      onClose={pending ? undefined : onClose}
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

/**
 * 되돌릴 것이 없는 결과 안내. 확인 버튼 하나뿐이다.
 *
 * 왼쪽 4px 띠가 디자인이 정한 "이건 거절이다" 표시다. 에러 블록과 달리 화면이
 * 망가진 것이 아니라 요청이 규칙에 걸렸다는 뜻이다.
 */
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
