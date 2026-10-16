"use client";

import Link from "next/link";
import { useActionState, useState } from "react";

import { finalizeInvoicesAction } from "@/app/(console)/billing/actions";
import { FINALIZE_INVOICES_IDLE } from "@/app/(console)/billing/state";
import { Dialog } from "@/components/screen/Dialog";

type FinalizeTarget = {
  month: string;
  monthLabel: string;
  targetCount: number;
  supplyAmount: string;
  isUnfinishedMonth: boolean;
  hasUnpricedUsages: boolean;
};

const CAUTION_STYLE = {
  margin: 0,
  paddingLeft: 10,
  borderLeft: "4px solid var(--color-accent)",
  color: "var(--color-accent-700)",
  textWrap: "pretty",
} as const;

export function InvoiceFinalizeDialog(target: FinalizeTarget) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        className="btn btn-primary"
        disabled={target.targetCount === 0}
        title={target.targetCount === 0 ? "청구할 금액이 있는 고객이 없습니다" : undefined}
        onClick={() => setIsOpen(true)}
      >
        인보이스 확정
      </button>

      {isOpen ? <InvoiceFinalizeForm {...target} onClose={() => setIsOpen(false)} /> : null}
    </>
  );
}

function InvoiceFinalizeForm({ onClose, ...target }: FinalizeTarget & { onClose: () => void }) {
  const [{ month, monthLabel, targetCount, supplyAmount, isUnfinishedMonth, hasUnpricedUsages }] =
    useState(target);
  const [state, formAction, isPending] = useActionState(
    finalizeInvoicesAction,
    FINALIZE_INVOICES_IDLE,
  );

  if (state.status === "succeeded") {
    return (
      <Dialog key="result" labelledBy="invoice-finalize-title" onClose={onClose}>
        <div className="dialog-title" id="invoice-finalize-title">
          {state.finalizedCount > 0
            ? `${monthLabel} 인보이스 ${state.finalizedCount}건을 확정했습니다`
            : "새로 확정한 인보이스가 없습니다"}
        </div>
        <p className="dialog-body" style={{ margin: 0, textWrap: "pretty" }}>
          {state.finalizedCount === 0
            ? `${monthLabel}에 청구할 금액이 있는 고객이 없거나 이미 모두 확정됐습니다.`
            : state.finalizedCount === targetCount
              ? "확정한 인보이스에는 공급가액에 세액 10%를 더한 합계가 찍혔습니다."
              : `창을 연 뒤 사용량이 바뀌어 처음 안내한 ${targetCount}곳과 다릅니다. 금액은 인보이스에서 확인하세요.`}
        </p>
        <div className="dialog-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            닫기
          </button>
          <Link className="btn btn-primary" href={`/invoices?month=${month}`}>
            인보이스 보기
          </Link>
        </div>
      </Dialog>
    );
  }

  return (
    <Dialog
      labelledBy="invoice-finalize-title"
      onClose={isPending ? undefined : onClose}
      action={formAction}
    >
      <div className="dialog-title" id="invoice-finalize-title">
        {monthLabel} 인보이스를 확정할까요?
      </div>

      <input type="hidden" name="month" value={month} />

      <p className="dialog-body" style={{ margin: 0, textWrap: "pretty" }}>
        청구할 금액이 있는 고객 {targetCount}곳의 인보이스를 확정합니다. 공급가액 합은{" "}
        {supplyAmount}이고, 인보이스에는 세액 10%를 더한 합계가 찍힙니다. 금액은 확정하는 순간의
        사용량으로 다시 계산합니다.
      </p>
      <p className="dialog-body" style={{ margin: 0, textWrap: "pretty" }}>
        확정한 인보이스는 고치거나 지울 수 없습니다. 확정한 고객에게 그 뒤 이 달에 들어온
        이벤트는 청구되지 않습니다.
      </p>
      <p className="dialog-body" style={{ margin: 0, textWrap: "pretty" }}>
        청구할 금액이 없는 고객은 이번에 확정되지 않습니다. 나중에 금액이 생기면 다시 확정할 수
        있습니다.
      </p>

      {isUnfinishedMonth ? (
        <p className="dialog-body" style={CAUTION_STYLE}>
          {monthLabel}은 아직 끝나지 않았습니다. 지금 확정하면 확정한 고객의 남은 기간 사용량은
          청구되지 않습니다.
        </p>
      ) : null}

      {hasUnpricedUsages ? (
        <p className="dialog-body" style={CAUTION_STYLE}>
          확정할 고객에게 단가 없는 사용량이 있습니다. 이 사용량은 청구되지 않고, 확정한 뒤에 단가를
          등록해도 이 달 인보이스에는 실리지 않습니다.
        </p>
      ) : null}

      {state.status === "failed" ? (
        <p style={{ margin: 0, fontSize: 12.5, color: "var(--color-accent-700)" }}>
          {state.message}
        </p>
      ) : null}

      <div className="dialog-actions">
        <button type="button" className="btn btn-secondary" disabled={isPending} onClick={onClose}>
          취소
        </button>
        <button type="submit" className="btn btn-primary" disabled={isPending}>
          {isPending ? "확정 중..." : "확정"}
        </button>
      </div>
    </Dialog>
  );
}
