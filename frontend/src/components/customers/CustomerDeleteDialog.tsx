"use client";

import { CloseOutlined } from "@ant-design/icons";
import { Alert, App, Button, Flex, Modal, theme, Typography } from "antd";
import { useActionState, useEffect, useRef, useTransition } from "react";

import { deleteCustomerAction } from "@/app/(console)/customers/actions";
import { CUSTOMER_DELETE_IDLE } from "@/app/(console)/customers/state";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { alertIcon } from "@/components/screen/alert-icon";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import { MODAL_WIDTH } from "@/components/table/table-defaults";

function toDeleteFormData(id: string, name: string): FormData {
  const formData = new FormData();
  formData.set("id", id);
  formData.set("name", name);
  return formData;
}

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
  const [, startTransition] = useTransition();
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const isConfirming = state.status !== "rejected" && state.status !== "gone";

  useInitialFocus(cancelButtonRef, isConfirming);

  useEffect(() => {
    if (state.status === "done") {
      message.success("삭제했습니다");
      onClose();
    }
  }, [state, message, onClose]);

  if (state.status === "rejected") {
    return (
      <CustomerDeleteNotice
        title="삭제할 수 없습니다"
        body={`${state.name}로 수집된 이벤트가 있습니다. 이벤트가 있는 고객은 삭제할 수 없습니다.`}
        note="서버가 이벤트 존재 여부를 확인하고 거절했습니다 (409)."
        onClose={onClose}
      />
    );
  }

  if (state.status === "gone") {
    return (
      <CustomerDeleteNotice
        title="이미 삭제된 고객입니다"
        body={`${state.name}는 이 목록을 연 뒤에 삭제됐습니다. 목록에서 사라집니다.`}
        note="서버에 그 고객이 없습니다 (404)."
        onClose={onClose}
      />
    );
  }

  const confirmDelete = () =>
    startTransition(() => formAction(toDeleteFormData(customer.id, customer.name)));

  return (
    <Modal
      open
      width={MODAL_WIDTH}
      title="고객을 삭제할까요?"
      onCancel={isPending ? undefined : onClose}
      keyboard={!isPending}
      mask={{ closable: !isPending }}
      closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} />, disabled: isPending }}
      footer={[
        <Button key="cancel" ref={cancelButtonRef} disabled={isPending} onClick={onClose}>
          취소
        </Button>,
        <Button key="delete" type="primary" danger loading={isPending} onClick={confirmDelete}>
          {isPending ? "삭제 중..." : "삭제"}
        </Button>,
      ]}
    >
      <Flex vertical gap="small">
        <Flex vertical>
          <Typography.Text strong>{customer.name}</Typography.Text>
          <Typography.Text
            type="secondary"
            style={{ fontFamily: token.fontFamilyCode, fontSize: token.fontSizeSM }}
          >
            {customer.id}
          </Typography.Text>
        </Flex>
        <Typography.Paragraph style={{ margin: 0 }}>
          수집된 이벤트가 한 건이라도 있으면 삭제할 수 없습니다. 저장할 때 서버가 확인합니다.
        </Typography.Paragraph>
        {state.status === "failed" ? (
          <Alert type="error" showIcon icon={alertIcon("error")} title={state.message} />
        ) : null}
      </Flex>
    </Modal>
  );
}

function CustomerDeleteNotice({
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
  const confirmButtonRef = useRef<HTMLButtonElement>(null);

  useInitialFocus(confirmButtonRef);

  return (
    <Modal
      open
      width={MODAL_WIDTH}
      title={title}
      onCancel={onClose}
      closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} /> }}
      footer={
        <Button ref={confirmButtonRef} onClick={onClose}>
          확인
        </Button>
      }
    >
      <Flex vertical gap="small">
        <Alert type="warning" showIcon icon={alertIcon("warning")} title={body} />
        <Typography.Text type="secondary">{note}</Typography.Text>
      </Flex>
    </Modal>
  );
}
