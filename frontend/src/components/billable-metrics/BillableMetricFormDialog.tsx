"use client";

import { CloseOutlined } from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  Descriptions,
  Divider,
  Form,
  Flex,
  Input,
  InputNumber,
  type InputRef,
  Modal,
  Select,
  theme,
  Typography,
} from "antd";
import { useActionState, useEffect, useRef, useState, useTransition } from "react";

import {
  createBillableMetricAction,
  updateBillableMetricAction,
} from "@/app/(console)/billable-metrics/actions";
import {
  BILLABLE_METRIC_FORM_IDLE,
  type BillableMetricField,
  type BillableMetricRowView,
} from "@/app/(console)/billable-metrics/state";
import { alertIcon } from "@/components/screen/alert-icon";
import { EnterSubmitButton } from "@/components/screen/EnterSubmitButton";
import { clearFieldErrors } from "@/components/screen/form-errors";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import {
  METRIC_AGGREGATIONS,
  METRIC_ROUNDING_OPTIONS,
  metricCalculationExample,
  metricCalculationFormula,
  type MetricRoundingMode,
} from "@/lib/billable-metric-calculation";

const DIMENSION_PROPERTIES_JOINER = ", ";

type BillableMetricFormValues = {
  code: string;
  name: string;
  eventType: string;
  aggregation: string;
  targetProperty: string;
  percentile: number | null;
  unitSize: number | null;
  roundingMode: MetricRoundingMode;
  roundingPrecision: number | null;
  dimensionProperties: string;
};

const FORM_FIELD_BY_SERVER_FIELD: Record<BillableMetricField, keyof BillableMetricFormValues> = {
  code: "code",
  name: "name",
  event_type: "eventType",
  aggregation: "aggregation",
  target_property: "targetProperty",
  percentile: "percentile",
  unit_size: "unitSize",
  rounding_mode: "roundingMode",
  rounding_precision: "roundingPrecision",
  dimension_properties: "dimensionProperties",
};

const trimText = (value: string | undefined) => value?.trim();

const PASSING_RULE = { validator: () => Promise.resolve() };

function toFormData(values: BillableMetricFormValues, editingCode: string | undefined): FormData {
  const formData = new FormData();
  formData.set("code", editingCode ?? values.code ?? "");
  formData.set("name", values.name ?? "");
  formData.set("event_type", values.eventType ?? "");
  formData.set("aggregation", values.aggregation ?? "sum");
  formData.set("target_property", values.aggregation === "count" ? "" : values.targetProperty ?? "");
  formData.set("percentile", values.aggregation === "percentile" ? String(values.percentile ?? "") : "");
  formData.set("unit_size", String(values.unitSize ?? ""));
  formData.set("rounding_mode", values.roundingMode ?? "none");
  formData.set("rounding_precision", String(values.roundingPrecision ?? 0));
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
  const aggregation = Form.useWatch("aggregation", form) ?? billableMetric?.aggregation ?? "sum";
  const targetProperty = Form.useWatch("targetProperty", form) ?? billableMetric?.targetProperty ?? "";
  const percentile = Form.useWatch("percentile", form) ?? billableMetric?.percentile ?? 50;
  const unitSize = Form.useWatch("unitSize", form) ?? billableMetric?.unitSize ?? 1;
  const roundingMode = Form.useWatch("roundingMode", form) ?? billableMetric?.roundingMode ?? "none";
  const roundingPrecision = Form.useWatch("roundingPrecision", form) ?? billableMetric?.roundingPrecision ?? 0;
  const calculation = { aggregation, target_property: targetProperty, percentile, unit_size: unitSize, rounding_mode: roundingMode, rounding_precision: roundingPrecision };
  const aggregationDescription = METRIC_AGGREGATIONS.find((option) => option.value === aggregation)?.description;

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
      width="min(680px, calc(100vw - 32px))"
      style={{ top: 24 }}
      styles={{ body: { maxHeight: "calc(100dvh - 180px)", overflowY: "auto", paddingInlineEnd: 4 } }}
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
          aggregation: billableMetric?.aggregation ?? "sum",
          targetProperty: billableMetric?.targetProperty ?? "",
          percentile: billableMetric?.percentile ?? 50,
          unitSize: billableMetric?.unitSize ?? 1,
          roundingMode: billableMetric?.roundingMode ?? "none",
          roundingPrecision: billableMetric?.roundingPrecision ?? 0,
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
        <Form.Item name="aggregation" label="집계 함수" extra={aggregationDescription} rules={[{ required: true, message: "집계 함수를 선택하세요" }]}>
          <Select
            options={METRIC_AGGREGATIONS.map(({ value, label }) => ({ value, label }))}
            onChange={(value) => {
              if (value === "count") form.setFieldValue("targetProperty", "");
              if (value !== "percentile") form.setFieldValue("percentile", null);
              else if (form.getFieldValue("percentile") == null) form.setFieldValue("percentile", 50);
            }}
          />
        </Form.Item>
        {aggregation === "percentile" ? <Form.Item
          name="percentile"
          label="백분위 (0-100)"
          preserve={false}
          extra="예: 95는 P95, 50은 중앙값입니다."
          rules={[{ required: true, type: "number", min: 0, max: 100, message: "백분위는 0부터 100 사이의 숫자로 입력하세요" }]}
        ><InputNumber min={0} max={100} style={{ width: "100%" }} /></Form.Item> : null}
        {aggregation !== "count" ? (
        <Form.Item
          name="targetProperty"
          label="집계 대상 속성"
          preserve={false}
          extra={aggregation === "count_distinct" ? "이벤트 properties에서 고유 값을 셀 키입니다. 예: user_id." : "이벤트 properties에서 집계할 숫자 속성의 키입니다. 없는 값과 숫자가 아닌 값은 제외합니다."}
          rules={[
            {
              required: true,
              transform: trimText,
              message: "집계 대상 속성을 입력하세요",
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
        ) : null}
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
        <Divider />
        <Typography.Title level={5}>결과 변환</Typography.Title>
        <Typography.Paragraph type="secondary">
          집계 결과를 과금 단위 크기로 나눕니다. 올림, 내림, 반올림을 선택하면 각 조합의 수량에 적용한 뒤 요금을 계산합니다.
        </Typography.Paragraph>
        <Form.Item name="unitSize" label="과금 단위 크기" extra="1이면 그대로 사용합니다. 예: 바이트를 1,024로 나누면 KiB 단위 수량이 됩니다." rules={[
          { required: true, message: "과금 단위 크기를 입력하세요" },
          { validator: (_, value) => typeof value === "number" && Number.isFinite(value) && value > 0 ? Promise.resolve() : Promise.reject(new Error("과금 단위 크기는 0보다 커야 합니다")) },
        ]}>
          <InputNumber min={0} style={{ width: "100%" }} />
        </Form.Item>
        <Flex gap={16} wrap>
          <Form.Item name="roundingMode" label="반올림 방식" style={{ flex: "1 1 240px" }}>
            <Select options={METRIC_ROUNDING_OPTIONS} onChange={(value) => { if (value === "none") form.setFieldValue("roundingPrecision", 0); }} />
          </Form.Item>
          {roundingMode !== "none" ? <Form.Item name="roundingPrecision" label="소수 자릿수" extra="0이면 정수로 만듭니다." style={{ flex: "1 1 180px" }} rules={[{ required: true, type: "integer", min: 0, max: 12, message: "소수 자릿수는 0부터 12 사이의 정수로 입력하세요" }]}>
            <InputNumber min={0} max={12} precision={0} style={{ width: "100%" }} />
          </Form.Item> : null}
        </Flex>
        <Alert type="info" title="계산 미리보기" description={<>
          <Typography.Paragraph style={{ fontFamily: token.fontFamilyCode, overflowWrap: "anywhere", marginBlock: 8 }}>{metricCalculationFormula(calculation)}</Typography.Paragraph>
          <span>{metricCalculationExample(calculation)}</span>
        </>} />
        {isEdit ? <Typography.Paragraph type="secondary" style={{ marginTop: token.margin }}>
          수집된 이벤트가 있는 미터는 이름만 바꿀 수 있습니다. 계산 기준을 바꾸려면 새 미터를 등록하세요.
        </Typography.Paragraph> : null}
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
        상단에 표시된 도입사에 저장합니다. 같은 이벤트와 속성으로 미터를 여러 개 만들 수 있습니다. 예를 들어 합계와 이벤트 수 미터에 각각 단가를 등록하면 두 사용료가 모두 청구됩니다.
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
