"use client";

import { CloseOutlined } from "@ant-design/icons";
import { Alert, Button, Flex, Modal, Typography } from "antd";
import { startTransition, useActionState, useRef, useState } from "react";

import { finalizeInvoicesAction } from "@/app/(console)/billing/actions";
import { FINALIZE_INVOICES_IDLE } from "@/app/(console)/billing/state";
import { useTenantScope } from "@/components/auth/TenantAccess";
import { InvoiceFinalizeButton } from "@/components/billing/InvoiceFinalizeButton";
import { alertIcon } from "@/components/screen/alert-icon";
import { LinkButton } from "@/components/screen/LinkButton";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import { useMutationScope } from "@/components/auth/use-mutation-scope";
import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";
import { ENVIRONMENT_LABELS, readEnvironment } from "@/lib/environment";
import { formatKrw } from "@/lib/format";
import { MODAL_WIDTH } from "@/components/table/table-defaults";

type FinalizeTarget = {
  month: string;
  monthLabel: string;
  targetCount: number;
  supplyAmount: string;
  isUnfinishedMonth: boolean;
  hasUnpricedUsages: boolean;
};

const PARAGRAPH_STYLE = { margin: 0, textWrap: "pretty" } as const;

function toFinalizeFormData(month: string): FormData {
  const formData = new FormData();
  formData.set("month", month);
  return formData;
}


// 확정 결과는 처음 몇 건만 보이고 나머지는 이미 전체 목록인 인보이스 화면으로 보낸다.
const FINALIZED_PREVIEW_COUNT = 5;
export function InvoiceFinalizeDialog(target: FinalizeTarget) {
  const [isOpen, setIsOpen] = useState(false);
  const isEmpty = target.targetCount === 0;
  const canManage = useTenantScope("admin");
  if (!canManage) return null;

  return (
    <>
      <InvoiceFinalizeButton
        disabled={isEmpty}
        disabledReason={isEmpty ? "청구할 금액이 있는 고객이 없거나 이미 모두 확정됐습니다." : undefined}
        onClick={() => setIsOpen(true)}
      />

      {isOpen ? <InvoiceFinalizeForm {...target} onClose={() => setIsOpen(false)} /> : null}
    </>
  );
}

function InvoiceFinalizeForm({ onClose, ...target }: FinalizeTarget & { onClose: () => void }) {
  const [{ month, monthLabel, targetCount, supplyAmount, isUnfinishedMonth, hasUnpricedUsages }] =
    useState(target);
  const search = useWorkspaceSearch();
  const [environment] = useState(() => readEnvironment(search.get("environment")));
  const [state, formAction, isPending] = useActionState(
    finalizeInvoicesAction,
    FINALIZE_INVOICES_IDLE,
  );
  const isSucceeded = state.status === "succeeded";
  const bindScope = useMutationScope();
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useInitialFocus(cancelButtonRef, !isSucceeded);
  useInitialFocus(closeButtonRef, isSucceeded);

  if (state.status === "succeeded") {
    return (
      <Modal
        key="result"
        open
        width={MODAL_WIDTH}
        title={
          state.finalizedCount > 0
            ? `${monthLabel} 인보이스 ${state.finalizedCount}건을 확정했습니다`
            : "새로 확정한 인보이스가 없습니다"
        }
        onCancel={onClose}
        closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} /> }}
        footer={[
          <Button key="close" ref={closeButtonRef} onClick={onClose}>
            닫기
          </Button>,
          <LinkButton key="invoices" type="primary" href={`/invoices?month=${month}`}>
            인보이스 보기
          </LinkButton>,
        ]}
      >
        <p>실제 확정 공급가액 {formatKrw(state.supplyAmount)}, 세액 {formatKrw(state.taxAmount)}, 총액 {formatKrw(state.totalAmount)}</p>
        <ul>{state.invoices.slice(0, FINALIZED_PREVIEW_COUNT).map(invoice => <li key={invoice.id}><LinkButton href={`/payments/${invoice.id}`}>{invoice.invoice_number ?? invoice.id}</LinkButton> {invoice.customer_name} / {formatKrw(invoice.total_amount)}</li>)}</ul>
        {state.invoices.length > FINALIZED_PREVIEW_COUNT && <p className="field-hint">{`나머지 ${state.invoices.length - FINALIZED_PREVIEW_COUNT}건은 '인보이스 보기'에서 확인하세요.`}</p>}
        <Typography.Paragraph style={PARAGRAPH_STYLE}>
          {state.failedCount > 0
            ? `확정하지 못한 고객이 ${state.failedCount}곳 있습니다. 확정한 고객은 그대로 남고, 다시 확정하면 남은 고객만 다시 시도합니다.`
            : state.finalizedCount === 0
              ? `${monthLabel}에 청구할 금액이 있는 고객이 없거나 이미 모두 확정됐습니다.`
              : state.finalizedCount === targetCount
                ? "확정한 인보이스에는 공급가액에 세액 10%를 더한 합계가 찍혔습니다."
                : `창을 연 뒤 사용량이 바뀌었거나 다른 확정이 먼저 처리한 고객이 있어 처음 안내한 ${targetCount}곳과 다릅니다. 금액은 인보이스에서 확인하세요.`}
        </Typography.Paragraph>
      </Modal>
    );
  }

  return (
    <Modal
      key="confirm"
      open
      width={MODAL_WIDTH}
      title={`${ENVIRONMENT_LABELS[environment]} ${monthLabel} 인보이스를 확정할까요?`}
      onCancel={isPending ? undefined : onClose}
      keyboard={!isPending}
      mask={{ closable: !isPending }}
      closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} />, disabled: isPending }}
      footer={[
        <Button key="cancel" ref={cancelButtonRef} disabled={isPending} onClick={onClose}>
          취소
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={isPending}
          onClick={() => startTransition(() => formAction(bindScope(toFinalizeFormData(month))))}
        >
          {isPending ? "확정 중..." : "확정"}
        </Button>,
      ]}
    >
      <Flex vertical gap="medium">
        <Typography.Paragraph style={PARAGRAPH_STYLE}>
          청구할 금액이 있는 고객 {targetCount}곳의 인보이스를 확정합니다. 공급가액 합은{" "}
          {supplyAmount}이고, 인보이스에는 세액 10%를 더한 합계가 찍힙니다. 금액은 집계 마감
          시각(다음 달 1일 13:00)까지 받은 사용량으로 다시 계산하고, 마감 전이면 확정하는 순간까지
          받은 사용량으로 계산합니다.
        </Typography.Paragraph>
        <Typography.Paragraph style={PARAGRAPH_STYLE}>
          확정한 인보이스는 고치거나 지울 수 없습니다. 확정한 고객에게 그 뒤 이 달에 들어온
          이벤트는 청구되지 않습니다.
        </Typography.Paragraph>
        <Typography.Paragraph style={PARAGRAPH_STYLE}>
          청구할 금액이 없는 고객은 이번에 확정되지 않습니다. 나중에 금액이 생기면 다시 확정할 수
          있습니다.
        </Typography.Paragraph>

        {isUnfinishedMonth ? (
          <Alert
            type="warning"
            showIcon
            icon={alertIcon("warning")}
            title={`${monthLabel}은 아직 끝나지 않았습니다. 지금 확정하면 확정한 고객의 남은 기간 사용량은 청구되지 않습니다.`}
          />
        ) : null}

        {hasUnpricedUsages ? (
          <Alert
            type="warning"
            showIcon
            icon={alertIcon("warning")}
            title="확정할 고객에게 단가 없는 사용량이 있습니다. 이 사용량은 청구되지 않고, 확정한 뒤에 단가를 등록해도 이 달 인보이스에는 실리지 않습니다."
          />
        ) : null}

        {state.status === "failed" ? (
          <Alert type="error" showIcon icon={alertIcon("error")} title={state.message} />
        ) : null}
      </Flex>
    </Modal>
  );
}
