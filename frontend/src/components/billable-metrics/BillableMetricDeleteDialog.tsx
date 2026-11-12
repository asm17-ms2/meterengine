"use client";

import { CloseOutlined } from "@ant-design/icons";
import { Alert, App, Button, Flex, Modal, theme, Typography } from "antd";
import { useActionState, useEffect, useRef, useTransition } from "react";

import { deleteBillableMetricAction } from "@/app/(console)/billable-metrics/actions";
import {
  BILLABLE_METRIC_DELETE_IDLE,
  type BillableMetricRowView,
} from "@/app/(console)/billable-metrics/state";
import { alertIcon } from "@/components/screen/alert-icon";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import { MODAL_WIDTH } from "@/components/table/table-defaults";
import { useMutationScope } from "@/components/auth/use-mutation-scope";

function toDeleteFormData(code: string, name: string): FormData {
  const formData = new FormData();
  formData.set("code", code);
  formData.set("name", name);
  return formData;
}

export function BillableMetricDeleteDialog({
  billableMetric,
  onClose,
}: {
  billableMetric: BillableMetricRowView;
  onClose: () => void;
}) {
  const bindScope = useMutationScope();
  const [state, formAction, isPending] = useActionState(
    deleteBillableMetricAction,
    BILLABLE_METRIC_DELETE_IDLE,
  );
  const [, startTransition] = useTransition();
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const isConfirming = state.status !== "rejected" && state.status !== "gone";

  useInitialFocus(cancelButtonRef, isConfirming);

  useEffect(() => {
    if (state.status === "done") {
      message.success("보관했어요");
      onClose();
    }
  }, [state, message, onClose]);

  if (state.status === "rejected") {
    return state.reason === "events" ? (
      <Notice
        title="보관할 수 없어요"
        body={`수집된 이벤트가 있어 ${state.name} 미터를 보관하지 못했어요. 관련 이벤트와 적용 기간을 확인하세요.`}
        onClose={onClose}
      />
    ) : (
      <Notice
        title="보관할 수 없어요"
        body={`등록된 단가가 있어 ${state.name} 미터를 보관하지 못했어요. 단가와 적용 기간을 확인하세요.`}
        onClose={onClose}
      />
    );
  }

  if (state.status === "gone") {
    return (
      <Notice
        title="미터를 찾을 수 없어요"
        body={`${state.name} 미터를 찾지 못했어요. 목록을 새로 조회하세요.`}
        onClose={onClose}
      />
    );
  }

  return (
    <Modal
      open
      width={MODAL_WIDTH}
      title="미터를 보관할까요?"
      onCancel={isPending ? undefined : onClose}
      keyboard={!isPending}
      mask={{ closable: !isPending }}
      closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} />, disabled: isPending }}
      footer={[
        <Button key="cancel" ref={cancelButtonRef} disabled={isPending} onClick={onClose}>
          취소
        </Button>,
        <Button
          key="delete"
          danger
          type="primary"
          loading={isPending}
          onClick={() =>
            startTransition(() =>
              formAction(bindScope(toDeleteFormData(billableMetric.code, billableMetric.name))),
            )
          }
        >
          {isPending ? "보관 중..." : "보관"}
        </Button>,
      ]}
    >
      <Flex vertical gap="small">
        <Flex vertical gap={2}>
          <Typography.Text strong>{billableMetric.name}</Typography.Text>
          <Typography.Text
            type="secondary"
            style={{ fontFamily: token.fontFamilyCode, fontSize: token.fontSizeSM }}
          >
            {billableMetric.code}
          </Typography.Text>
        </Flex>
        <Typography.Paragraph style={{ margin: 0 }}>
          보관하면 사용 중 목록에서 빠지고 다음 달부터 집계하지 않아요. 이번 달 사용량과 청구, 지난 기록은 그대로 남고 보관 목록에서 복원할 수 있어요.
        </Typography.Paragraph>
        {state.status === "failed" ? (
          <Alert type="error" showIcon icon={alertIcon("error")} title={state.message} />
        ) : null}
      </Flex>
    </Modal>
  );
}

function Notice({
  title,
  body,
  onClose,
}: {
  title: string;
  body: string;
  onClose: () => void;
}) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  useInitialFocus(confirmRef);

  return (
    <Modal
      key="notice"
      open
      width={MODAL_WIDTH}
      title={title}
      onCancel={onClose}
      closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} /> }}
      footer={
        <Button ref={confirmRef} onClick={onClose}>
          확인
        </Button>
      }
    >
      <Flex vertical gap="small">
        <Alert type="warning" showIcon icon={alertIcon("warning")} title={body} />
      </Flex>
    </Modal>
  );
}
