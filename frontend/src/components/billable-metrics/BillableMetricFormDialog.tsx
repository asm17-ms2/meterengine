"use client";

import { CloseOutlined } from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  Descriptions,
  Divider,
  Form,
  Input,
  type InputRef,
  Modal,
  Tag,
  theme,
  Typography,
} from "antd";
import { useActionState, useEffect, useRef, useState, useTransition } from "react";

import {
  createBillableMetricAction,
  updateBillableMetricAction,
} from "@/app/(console)/billable-metrics/actions";
import {
  BILLABLE_METRIC_AGGREGATION,
  BILLABLE_METRIC_FORM_IDLE,
  type BillableMetricField,
  type BillableMetricRowView,
} from "@/app/(console)/billable-metrics/state";
import { alertIcon } from "@/components/screen/alert-icon";
import { EnterSubmitButton } from "@/components/screen/EnterSubmitButton";
import { clearFieldErrors } from "@/components/screen/form-errors";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import { MODAL_WIDTH } from "@/components/table/table-defaults";

const DIMENSION_PROPERTIES_JOINER = ", ";

type BillableMetricFormValues = {
  code: string;
  name: string;
  eventType: string;
  targetProperty: string;
  dimensionProperties: string;
};

const FORM_FIELD_BY_SERVER_FIELD: Record<BillableMetricField, keyof BillableMetricFormValues> = {
  code: "code",
  name: "name",
  event_type: "eventType",
  target_property: "targetProperty",
  dimension_properties: "dimensionProperties",
};

const trimText = (value: string | undefined) => value?.trim();

const PASSING_RULE = { validator: () => Promise.resolve() };

function toFormData(values: BillableMetricFormValues, editingCode: string | undefined): FormData {
  const formData = new FormData();
  formData.set("code", editingCode ?? values.code ?? "");
  formData.set("name", values.name ?? "");
  formData.set("event_type", values.eventType ?? "");
  formData.set("target_property", values.targetProperty ?? "");
  formData.set("dimension_properties", values.dimensionProperties ?? "");
  return formData;
}

export function BillableMetricFormDialog({
  billableMetric,
  onClose,
}: {
  billableMetric: BillableMetricRowView | null;
  onClose: () => void;
}) {
  const isEdit = billableMetric !== null;
  const [state, formAction, isPending] = useActionState(
    isEdit ? updateBillableMetricAction : createBillableMetricAction,
    BILLABLE_METRIC_FORM_IDLE,
  );
  const [form] = Form.useForm<BillableMetricFormValues>();
  const [, startTransition] = useTransition();
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const firstFieldRef = useRef<InputRef>(null);
  const [dismissedState, setDismissedState] = useState<typeof state | null>(null);
  const dismissFailed = () => setDismissedState(state);
  const monoStyle = { fontFamily: token.fontFamilyCode };

  useInitialFocus(firstFieldRef);

  useEffect(() => {
    if (state.status === "done") {
      message.success("저장했습니다");
      onClose();
    }
    if (state.status === "invalid") {
      form.setFields(
        Object.entries(state.fieldErrors).map(([field, error]) => ({
          name: FORM_FIELD_BY_SERVER_FIELD[field as BillableMetricField],
          errors: [error],
        })),
      );
    }
  }, [state, form, message, onClose]);

  const submit = (values: BillableMetricFormValues) => {
    clearFieldErrors(form);
    startTransition(() => formAction(toFormData(values, billableMetric?.code)));
  };

  const submitForm = () => form.submit();

  return (
    <Modal
      open
      forceRender
      width={MODAL_WIDTH}
      title={isEdit ? "미터 수정" : "미터 등록"}
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
      <Form<BillableMetricFormValues>
        form={form}
        layout="vertical"
        disabled={isPending}
        onFinish={submit}
        onValuesChange={dismissFailed}
        onFinishFailed={dismissFailed}
        initialValues={{
          code: billableMetric?.code ?? "",
          name: billableMetric?.name ?? "",
          eventType: billableMetric?.eventType ?? "",
          targetProperty: billableMetric?.targetProperty ?? "",
          dimensionProperties:
            billableMetric?.dimensionProperties.join(DIMENSION_PROPERTIES_JOINER) ?? "",
        }}
      >
        {isEdit ? null : (
          <Form.Item
            name="code"
            label="코드"
            extra="조직 내에서 유일해야 합니다. 등록 뒤 바꿀 수 없습니다."
            rules={[{ required: true, transform: trimText, message: "코드를 입력하세요" }]}
          >
            <Input
              ref={firstFieldRef}
              placeholder="input-tokens"
              spellCheck={false}
              autoComplete="off"
              style={monoStyle}
            />
          </Form.Item>
        )}
        <Form.Item
          name="name"
          label="이름"
          rules={[{ required: true, transform: trimText, message: "이름을 입력하세요" }]}
        >
          <Input
            ref={isEdit ? firstFieldRef : undefined}
            placeholder="입력 토큰"
            autoComplete="off"
          />
        </Form.Item>
        <Form.Item
          name="eventType"
          label="이벤트 타입"
          extra="어떤 사용량 이벤트를 집계할지 정하는 매칭 키입니다."
          rules={[{ required: true, transform: trimText, message: "이벤트 타입을 입력하세요" }]}
        >
          <Input
            placeholder="llm_request"
            spellCheck={false}
            autoComplete="off"
            style={monoStyle}
          />
        </Form.Item>
        <Form.Item label="집계 함수" extra="지금은 sum만 지원합니다.">
          <Tag color="processing">{BILLABLE_METRIC_AGGREGATION}</Tag>
        </Form.Item>
        <Form.Item
          name="targetProperty"
          label="집계 대상 속성"
          extra="이벤트 properties에서 합산할 키입니다. sum 집계는 필수입니다."
          rules={[
            {
              required: true,
              transform: trimText,
              message: "집계 대상 속성을 입력하세요. sum 집계는 필수입니다",
            },
          ]}
        >
          <Input
            placeholder="input_tokens"
            spellCheck={false}
            autoComplete="off"
            style={monoStyle}
          />
        </Form.Item>
        <Form.Item
          name="dimensionProperties"
          label="차원 키"
          extra="단가를 가르는 이벤트 속성 키. 예: model. 여럿이면 쉼표로 나눕니다."
          rules={[PASSING_RULE]}
        >
          <Input
            placeholder="model"
            spellCheck={false}
            autoComplete="off"
            style={monoStyle}
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
              key: "code",
              label: "코드",
              children: <Typography.Text style={monoStyle}>{billableMetric.code}</Typography.Text>,
            },
          ]}
        />
      ) : null}

      <Divider size="small" />
      <Typography.Paragraph type="secondary" style={{ margin: 0, fontSize: token.fontSizeSM }}>
        도입사는 요청에 사용하는 API 키로 정해집니다.
      </Typography.Paragraph>

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
