"use client";

import { useActionState, useEffect, useState } from "react";

import {
  createCustomerAction,
  renameCustomerAction,
} from "@/app/(console)/customers/actions";
import { FORM_IDLE } from "@/app/(console)/customers/state";
import type { CustomerRowView } from "@/components/customers/CustomerTable";
import { Dialog } from "@/components/screen/Dialog";

/**
 * 고객 등록/수정 폼.
 *
 * 등록과 수정을 한 컴포넌트로 둔 이유: 고칠 수 있는 것이 이름 하나뿐이라 두 폼의
 * 입력이 완전히 같다. 다른 것은 제목, 서버 액션, 그리고 수정에만 붙는 읽기 전용
 * 정보(고객 ID와 등록일)뿐이다.
 *
 * 검증을 클라이언트에 두지 않고 서버 액션에 맡긴다. 어차피 저장을 눌러야 나오는
 * 오류이고, 같은 규칙을 두 곳에 두면 한쪽만 고쳐지는 날이 온다.
 */
export function CustomerFormDialog({
  customer,
  onClose,
}: {
  /** 없으면 등록, 있으면 그 고객의 수정. */
  customer: CustomerRowView | null;
  onClose: () => void;
}) {
  const isEdit = customer !== null;
  const [state, formAction, pending] = useActionState(
    isEdit ? renameCustomerAction : createCustomerAction,
    FORM_IDLE,
  );

  /*
   * 입력을 controlled로 잡는다. React가 form action 제출 뒤 폼을 리셋하기 때문에
   * defaultValue로 두면 검증에 걸려 다시 그려질 때 방금 친 이름이 사라진다.
   * "고객명을 입력하세요"라고 말하면서 입력칸을 비워 버리는 꼴이 된다.
   */
  const [name, setName] = useState(isEdit ? customer.name : "");

  useEffect(() => {
    // 저장이 끝나면 닫는다. 서버 액션은 화면을 조작할 수 없어서 상태로 알린다.
    // 목록은 액션 안의 revalidatePath가 이미 새로 읽게 해 두었다.
    if (state.status === "done") onClose();
  }, [state, onClose]);

  const message =
    state.status === "invalid" || state.status === "failed"
      ? state.message
      : null;

  return (
    <Dialog
      labelledBy="customer-form-title"
      // 저장 중에는 닫히지 않는다. 요청이 날아간 뒤 창만 사라지면 결과를 알 수 없다.
      onClose={pending ? undefined : onClose}
      action={formAction}
    >
      <div className="dialog-title" id="customer-form-title">
        {isEdit ? "고객 수정" : "고객 등록"}
      </div>

      {isEdit ? <input type="hidden" name="id" value={customer.id} /> : null}

      <div className="field">
        <label htmlFor="customer-name">
          고객명 <span style={{ color: "var(--color-accent)" }}>*</span>
        </label>
        <input
          id="customer-name"
          name="name"
          className="input"
          value={name}
          onChange={(event) => setName(event.target.value)}
          disabled={pending}
          autoFocus
          maxLength={255}
          aria-invalid={message !== null}
          aria-describedby={message ? "customer-name-error" : undefined}
          style={
            message
              ? {
                  borderColor: "var(--color-accent)",
                  background: "var(--color-accent-100)",
                }
              : { borderColor: "var(--color-accent)" }
          }
        />
        {message ? (
          <p
            id="customer-name-error"
            style={{
              margin: "6px 0 0",
              fontSize: 12.5,
              color: "var(--color-accent-700)",
            }}
          >
            {message}
          </p>
        ) : null}
      </div>

      {isEdit ? (
        // 서버가 발급한 값이라 고칠 수 없다. 여기 있는 이유는 목록에서 이름만
        // 보고 연 창에서 "어느 고객을 고치는 중인지" 확인할 수 있어야 해서다.
        <dl
          className="detail-grid"
          style={{ gridTemplateColumns: "88px minmax(0, 1fr)" }}
        >
          <dt>고객 ID</dt>
          <dd className="detail-grid__mono">{customer.id}</dd>
          <dt>등록일</dt>
          <dd>{customer.createdAt}</dd>
        </dl>
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
          {pending ? "저장 중..." : "저장"}
        </button>
      </div>
    </Dialog>
  );
}
