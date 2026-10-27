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
      message.success("보관했습니다");
      onClose();
    }
  }, [state, message, onClose]);

  if (state.status === "rejected") {
    return state.reason === "events" ? (
      <Notice
        title="보관할 수 없습니다"
        body={`${state.name} 미터를 현재 상태에서 보관하지 못했습니다. 관련 이벤트와 적용 기간을 확인하세요.`}
        note="서버가 이벤트 존재를 확인하고 거절했습니다 (409)."
        onClose={onClose}
      />
    ) : (
      <Notice
        title="보관할 수 없습니다"
        body={`${state.name} 미터를 현재 상태에서 보관하지 못했습니다. 단가와 적용 기간을 확인하세요.`}
        note="서버가 단가 존재를 확인하고 거절했습니다 (409)."
        onClose={onClose}
      />
    );
  }

  if (state.status === "gone") {
    return (
      <Notice
        title="미터를 찾을 수 없습니다"
        body={`${state.name}를 서버에서 찾지 못했습니다. 목록을 다시 확인하세요.`}
        note="서버에 그 미터가 없습니다 (404)."
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
          보관하면 관리 목록에서 분리되고 다음 달부터 집계를 중단합니다. 현재 월의 사용량과 청구, 과거 기록은 유지되며 보관 목록에서 복원할 수 있습니다.
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
  note,
  onClose,
}: {
  title: string;
  body: string;
  note: string;
  onClose: () => void;
}) {
  const { token } = theme.useToken();
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
        <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
          {note}
        </Typography.Text>
      </Flex>
    </Modal>
  );
}
