"use client";

import { CloseOutlined } from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  Descriptions,
  Form,
  Input,
  Modal,
  theme,
  Typography,
  type InputRef,
} from "antd";
import { useActionState, useEffect, useRef, useState, useTransition } from "react";

import {
  createCustomerAction,
  updateCustomerAction,
} from "@/app/(console)/customers/actions";
import { CUSTOMER_FORM_IDLE } from "@/app/(console)/customers/state";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { alertIcon } from "@/components/screen/alert-icon";
import { EnterSubmitButton } from "@/components/screen/EnterSubmitButton";
import { clearFieldErrors } from "@/components/screen/form-errors";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import { MODAL_WIDTH } from "@/components/table/table-defaults";

type CustomerFormValues = {
  name?: string;
  billingEmail?: string;
};

const NAME_MAX_LENGTH = 255;
const BILLING_EMAIL_MAX_LENGTH = 254;
const BILLING_EMAIL_PATTERN = /^\S+@\S+\.\S+$/;

function trimText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function trimTextOrUndefined(value: unknown): string | undefined {
  return trimText(value) || undefined;
}

function toCustomerFormData(values: CustomerFormValues, id: string | undefined): FormData {
  const formData = new FormData();
  if (id) formData.set("id", id);
  formData.set("name", values.name ?? "");
  formData.set("billing_email", values.billingEmail ?? "");
  return formData;
}

export function CustomerFormDialog({
  customer,
  onClose,
}: {
  /** 없으면 등록, 있으면 그 고객의 수정. */
  customer: CustomerRowView | null;
  onClose: () => void;
}) {
  const isEdit = customer !== null;
  const [state, formAction, isPending] = useActionState(
    isEdit ? updateCustomerAction : createCustomerAction,
    CUSTOMER_FORM_IDLE,
  );
  const [form] = Form.useForm<CustomerFormValues>();
  const [, startTransition] = useTransition();
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const nameInputRef = useRef<InputRef>(null);
  const [dismissedState, setDismissedState] = useState<typeof state | null>(null);
  const dismissFailed = () => setDismissedState(state);

  useInitialFocus(nameInputRef);

  useEffect(() => {
    if (state.status === "done") {
      message.success("저장했습니다");
      onClose();
    }
    if (state.status === "invalid") {
      form.setFields([{ name: state.field, errors: [state.message] }]);
    }
  }, [state, form, message, onClose]);

  const submit = (values: CustomerFormValues) => {
    clearFieldErrors(form);
    startTransition(() => formAction(toCustomerFormData(values, customer?.id)));
  };

  const submitForm = () => form.submit();

  return (
    <Modal
      open
      forceRender
      width={MODAL_WIDTH}
      title={isEdit ? "고객 수정" : "고객 등록"}
      onCancel={isPending ? undefined : onClose}
      keyboard={!isPending}
      mask={{ closable: !isPending }}
      closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} />, disabled: isPending }}
      footer={[
        <Button key="cancel" disabled={isPending} onClick={onClose}>
          취소
        </Button>,
        <Button key="submit" type="primary" loading={isPending} onClick={submitForm}>
          {isPending ? "저장 중..." : "저장"}
        </Button>,
      ]}
    >
      <Form<CustomerFormValues>
        form={form}
        layout="vertical"
        disabled={isPending}
        onFinish={submit}
        onValuesChange={dismissFailed}
        onFinishFailed={dismissFailed}
        initialValues={{
          name: customer?.name ?? "",
          billingEmail: customer?.billingEmail ?? "",
        }}
      >
        <Form.Item
          name="name"
          label="고객명"
          rules={[
            { required: true, transform: trimText, message: "고객명을 입력하세요" },
            {
              max: NAME_MAX_LENGTH,
              transform: trimText,
              message: "고객명은 255자를 넘을 수 없습니다",
            },
          ]}
        >
          <Input ref={nameInputRef} maxLength={NAME_MAX_LENGTH} />
        </Form.Item>
        <Form.Item
          name="billingEmail"
          label="청구 이메일"
          extra="확정한 인보이스의 청구 메일을 받을 주소입니다. 비워 둘 수 있습니다."
          validateTrigger={["onChange", "onBlur"]}
          rules={[
            {
              max: BILLING_EMAIL_MAX_LENGTH,
              transform: trimText,
              message: "청구 이메일은 254자를 넘을 수 없습니다",
            },
            {
              pattern: BILLING_EMAIL_PATTERN,
              transform: trimTextOrUndefined,
              validateTrigger: "onBlur",
              message: "올바른 형식의 이메일 주소여야 합니다",
            },
          ]}
        >
          <Input
            type="text"
            inputMode="email"
            autoComplete="email"
            spellCheck={false}
            maxLength={BILLING_EMAIL_MAX_LENGTH}
            placeholder="billing@example.com"
          />
        </Form.Item>
        <EnterSubmitButton disabled={isPending} />
      </Form>

      {isEdit ? (
        <Descriptions
          size="small"
          colon={false}
          column={1}
          items={[
            {
              key: "id",
              label: "고객 ID",
              children: (
                <Typography.Text style={{ fontFamily: token.fontFamilyCode }}>
                  {customer.id}
                </Typography.Text>
              ),
            },
            { key: "createdAt", label: "등록일", children: customer.createdAt },
          ]}
        />
      ) : null}

      {state.status === "failed" && state !== dismissedState ? (
        <Alert
          type="error"
          showIcon
          icon={alertIcon("error")}
          title={state.message}
          style={{ marginTop: token.margin }}
        />
      ) : null}
    </Modal>
  );
}
