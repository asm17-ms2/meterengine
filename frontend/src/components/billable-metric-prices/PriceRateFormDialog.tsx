"use client";

import { CloseOutlined } from "@ant-design/icons";
import { Alert, App, Button, Form, Input, type InputRef, Modal, theme } from "antd";
import { startTransition, useActionState, useEffect, useRef, useState } from "react";

import { createPriceRateAction } from "@/app/(console)/billable-metric-prices/actions";
import {
  PRICE_RATE_FORM_IDLE,
  type BillableMetricPriceRowView,
} from "@/app/(console)/billable-metric-prices/state";
import { alertIcon } from "@/components/screen/alert-icon";
import { EnterSubmitButton } from "@/components/screen/EnterSubmitButton";
import { clearFieldErrors } from "@/components/screen/form-errors";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import { ScreenNote } from "@/components/screen/ScreenNote";
import { MODAL_WIDTH } from "@/components/table/table-defaults";

type PriceRateFormValues = {
  dimensionValues?: Record<string, string>;
  unitPrice: string;
};

function toPriceRateFormData(
  code: string,
  dimensionProperties: string[],
  values: PriceRateFormValues,
): FormData {
  const formData = new FormData();
  formData.set("code", code);
  for (const dimensionProperty of dimensionProperties) {
    formData.append("dimension_key", dimensionProperty);
    formData.append("dimension_value", values.dimensionValues?.[dimensionProperty] ?? "");
  }
  formData.set("unit_price", values.unitPrice ?? "");
  return formData;
}

export function PriceRateFormDialog({
  billableMetricPrice,
  onClose,
}: {
  billableMetricPrice: BillableMetricPriceRowView;
  onClose: () => void;
}) {
  const [state, formAction, isPending] = useActionState(createPriceRateAction, PRICE_RATE_FORM_IDLE);
  const [form] = Form.useForm<PriceRateFormValues>();
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const firstFieldRef = useRef<InputRef>(null);
  const [dismissedState, setDismissedState] = useState<typeof state | null>(null);
  const dismissFailed = () => setDismissedState(state);
  const { code, name, dimensionProperties } = billableMetricPrice;
  const monoStyle = { fontFamily: token.fontFamilyCode };

  useInitialFocus(firstFieldRef);

  useEffect(() => {
    if (state.status === "done") {
      message.success("등록했습니다");
      onClose();
    }
  }, [state, message, onClose]);

  const submit = (values: PriceRateFormValues) => {
    clearFieldErrors(form);
    startTransition(() => formAction(toPriceRateFormData(code, dimensionProperties, values)));
  };

  return (
    <Modal
      open
      forceRender
      width={MODAL_WIDTH}
      title={`${name} 단가 등록`}
      onCancel={isPending ? undefined : onClose}
      keyboard={!isPending}
      mask={{ closable: !isPending }}
      closable={{ closeIcon: <CloseOutlined aria-hidden aria-label={undefined} />, disabled: isPending }}
      footer={[
        <Button key="cancel" disabled={isPending} onClick={onClose}>
          취소
        </Button>,
        <Button key="submit" type="primary" loading={isPending} onClick={() => form.submit()}>
          {isPending ? "등록 중..." : "등록"}
        </Button>,
      ]}
    >
      <Form<PriceRateFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        disabled={isPending}
        onFinish={submit}
        onValuesChange={dismissFailed}
        onFinishFailed={dismissFailed}
        initialValues={{ unitPrice: "" }}
      >
        {dimensionProperties.map((dimensionProperty, index) => (
          <Form.Item
            key={dimensionProperty}
            name={["dimensionValues", dimensionProperty]}
            label={`${dimensionProperty} 값`}
            rules={[{ required: true, whitespace: true, message: "차원 키마다 값을 입력하세요." }]}
          >
            <Input
              ref={index === 0 ? firstFieldRef : undefined}
              placeholder="claude-opus-5"
              autoComplete="off"
              style={monoStyle}
            />
          </Form.Item>
        ))}
        <Form.Item
          name="unitPrice"
          label="단가 (원)"
          rules={[{ required: true, whitespace: true, message: "단가를 입력하세요." }]}
        >
          <Input
            ref={dimensionProperties.length === 0 ? firstFieldRef : undefined}
            inputMode="decimal"
            placeholder="0.5"
            autoComplete="off"
            style={monoStyle}
          />
        </Form.Item>
        <EnterSubmitButton disabled={isPending} />
      </Form>
      <ScreenNote>같은 조합의 단가가 이미 있으면 새 단가로 덮어씁니다.</ScreenNote>
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
