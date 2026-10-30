"use client";

import { Alert, AutoComplete, Button, Descriptions, Input, Space, Tag, Typography } from "antd";
import { startTransition, useActionState, useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";

import { ingestEventAction, type EventSendState } from "@/app/events/actions";
import { ErrorViewAlert } from "@/components/ErrorViewAlert";
import { TargetSelect } from "@/components/TargetSelect";
import { formatKstTimestamp } from "@/lib/event-form";

export type CustomerOption = { id: string; name: string };

export type TargetCustomers = { name: string; customers: CustomerOption[]; error?: string };

const INITIAL_STATE: EventSendState = { status: "idle" };

type EventSendFormProps = {
  targets: TargetCustomers[];
  defaultTimestamp: string;
  configError?: string;
};

function FieldRow({ label, htmlFor, error, children }: { label: string; htmlFor?: string; error?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={htmlFor}>
        <Typography.Text strong>{label}</Typography.Text>
      </label>
      {children}
      {error && <p className="field-error" id={`${htmlFor}-error`}>{error}</p>}
    </div>
  );
}

function SendResult({ state }: { state: EventSendState }) {
  switch (state.status) {
    case "idle":
      return null;
    case "invalid":
      return <Alert type="error" title={`${state.field}: ${state.message}`} />;
    case "failed":
      return <ErrorViewAlert error={state.error} />;
    case "done":
      return (
        <div><p role="status" aria-live="polite">{state.response.duplicate ? "이미 접수한 이벤트입니다." : "이벤트를 접수했습니다."} 전송 ID: {state.response.transaction_id}</p><Descriptions
          bordered
          size="small"
          column={1}
          items={[
            {
              key: "result",
              label: "결과",
              children: state.response.duplicate ? <Tag color="orange">중복</Tag> : <Tag color="green">저장됨</Tag>,
            },
            { key: "transaction_id", label: "transaction_id", children: state.response.transaction_id },
          ]}
        /></div>
      );
  }
}

export function EventSendForm({ targets, defaultTimestamp, configError }: EventSendFormProps) {
  const [target, setTarget] = useState<string | undefined>(targets[0]?.name);
  const [customerId, setCustomerId] = useState("");
  const [timestamp, setTimestamp] = useState(defaultTimestamp);
  const [state, formAction, isPending] = useActionState(ingestEventAction, INITIAL_STATE);

  const formRef = useRef<HTMLFormElement>(null);
  useEffect(() => { if (state.status === "invalid") formRef.current?.querySelector<HTMLElement>(`[id="${CSS.escape(state.field)}"]`)?.focus(); }, [state]);
  const fieldError = (field: string) => state.status === "invalid" && state.field === field ? state.message : undefined;

  const selected = targets.find((candidate) => candidate.name === target);
  const customerOptions = (selected?.customers ?? []).map((customer) => ({
    value: customer.id,
    label: `${customer.name} (${customer.id})`,
  }));
  const hasPropertiesError = state.status === "invalid" && state.field === "properties";

  function changeTarget(next: string) {
    setTarget(next);
    setCustomerId("");
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    startTransition(() => formAction(formData));
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: "100%", maxWidth: 720 }}>
      <Typography.Title level={3}>이벤트 보내기</Typography.Title>
      {configError === undefined ? null : <Alert type="error" title="대상 설정을 읽지 못했습니다" description={configError} />}
      <form ref={formRef} onSubmit={submit} aria-busy={isPending}>
        <input type="hidden" name="target" value={target ?? ""} />
        <input type="hidden" name="customer_id" value={customerId} />
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <FieldRow label="대상">
            <div>
              <TargetSelect targetNames={targets.map((candidate) => candidate.name)} value={target} onChange={changeTarget} />
            </div>
          </FieldRow>
          {selected?.error === undefined ? null : (
            <Alert type="warning" title="고객 목록을 불러오지 못했습니다. customer_id를 직접 입력하세요." description={selected.error} />
          )}
          <FieldRow label="customer_id" htmlFor="customer_id" error={fieldError("customer_id")}>
            <AutoComplete
              id="customer_id" aria-invalid={Boolean(fieldError("customer_id"))} aria-describedby={fieldError("customer_id") ? "customer_id-error" : undefined}
              style={{ width: "100%" }}
              value={customerId}
              onChange={setCustomerId}
              options={customerOptions}
              placeholder="고객을 고르거나 id를 입력"
              filterOption={(input, option) => (option?.label ?? "").toLowerCase().includes(input.toLowerCase())}
            />
          </FieldRow>
          <FieldRow label="type" htmlFor="type" error={fieldError("type")}>
            <Input id="type" aria-invalid={Boolean(fieldError("type"))} aria-describedby={fieldError("type") ? "type-error" : undefined} name="type" placeholder="llm_request" />
          </FieldRow>
          <FieldRow label="timestamp" htmlFor="timestamp" error={fieldError("timestamp")}>
            <Space.Compact style={{ width: "100%" }}>
              <Input id="timestamp" aria-invalid={Boolean(fieldError("timestamp"))} aria-describedby={fieldError("timestamp") ? "timestamp-error" : undefined} name="timestamp" value={timestamp} onChange={(event) => setTimestamp(event.target.value)} />
              <Button onClick={() => setTimestamp(formatKstTimestamp(new Date()))}>지금</Button>
            </Space.Compact>
          </FieldRow>
          <FieldRow label="properties (JSON 객체)" htmlFor="properties" error={fieldError("properties")}>
            <Input.TextArea
              id="properties" aria-invalid={Boolean(fieldError("properties"))} aria-describedby={fieldError("properties") ? "properties-error" : undefined}
              name="properties"
              defaultValue="{}"
              autoSize={{ minRows: 4 }}
              status={hasPropertiesError ? "error" : undefined}
              style={{ fontFamily: "monospace" }}
            />
          </FieldRow>
          <FieldRow label="transaction_id (비우면 SDK가 만듭니다)" htmlFor="transaction_id" error={fieldError("transaction_id")}>
            <Input id="transaction_id" aria-invalid={Boolean(fieldError("transaction_id"))} aria-describedby={fieldError("transaction_id") ? "transaction_id-error" : undefined} name="transaction_id" />
          </FieldRow>
          <Button type="primary" htmlType="submit" loading={isPending} disabled={target === undefined}>
            보내기
          </Button>
        </Space>
      </form>
      <SendResult state={state} />
    </Space>
  );
}
