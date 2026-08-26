"use client";

import { useActionState, useEffect, useState } from "react";

import { registerMetricAction } from "@/app/(console)/metrics/actions";
import {
  METRIC_FORM_IDLE,
  type MetricField,
  type MetricRowView,
} from "@/app/(console)/metrics/state";
import { Dialog } from "@/components/screen/Dialog";

export function MetricFormDialog({
  onClose,
  onRegistered,
}: {
  onClose: () => void;
  onRegistered: (metric: MetricRowView) => void;
}) {
  const [state, formAction, pending] = useActionState(
    registerMetricAction,
    METRIC_FORM_IDLE,
  );
  const [values, setValues] = useState<Record<MetricField, string>>({
    code: "",
    name: "",
    event_type: "",
    target_property: "",
  });

  useEffect(() => {
    if (state.status === "done") {
      onRegistered(state.metric);
      onClose();
    }
  }, [state, onRegistered, onClose]);

  const fieldErrors = state.status === "invalid" ? state.fieldErrors : {};
  const message = state.status === "failed" ? state.message : null;

  const fieldProps = (field: MetricField) => ({
    value: values[field],
    onChange: (value: string) =>
      setValues((prev) => ({ ...prev, [field]: value })),
    error: fieldErrors[field],
    disabled: pending,
  });

  return (
    <Dialog
      labelledBy="metric-form-title"
      onClose={pending ? undefined : onClose}
      action={formAction}
    >
      <div className="dialog-title" id="metric-form-title">
        미터 등록
      </div>

      <TextField
        id="metric-code"
        name="code"
        label="코드"
        placeholder="input-tokens"
        hint="조직 내에서 유일해야 합니다."
        mono
        autoFocus
        {...fieldProps("code")}
      />
      <TextField
        id="metric-name"
        name="name"
        label="이름"
        placeholder="입력 토큰"
        {...fieldProps("name")}
      />
      <TextField
        id="metric-event-type"
        name="event_type"
        label="이벤트 타입"
        placeholder="llm_request"
        hint="어떤 사용량 이벤트를 집계할지 정하는 매칭 키입니다."
        mono
        {...fieldProps("event_type")}
      />

      <div className="field">
        <span
          style={{
            display: "block",
            fontSize: 12,
            marginBottom: 5,
            color: "var(--text-70)",
          }}
        >
          집계 함수
        </span>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span
            style={{
              display: "inline-flex",
              padding: "7px 12px",
              fontSize: 13,
              background: "var(--color-accent)",
              color: "var(--color-bg)",
            }}
          >
            SUM
          </span>
          <span className="screen-note">지금은 SUM만 지원합니다.</span>
        </div>
      </div>

      <TextField
        id="metric-target-property"
        name="target_property"
        label="집계 대상 속성"
        placeholder="input_tokens"
        hint="이벤트 properties에서 합산할 키입니다. SUM 집계는 필수입니다."
        mono
        {...fieldProps("target_property")}
      />

      <p
        className="screen-note"
        style={{
          paddingTop: 4,
          borderTop: "1px solid var(--color-divider)",
        }}
      >
        도입사는 폼이 아니라 공통 헤더{" "}
        <span style={{ fontFamily: "var(--mono)", fontSize: 11.5 }}>
          X-Organization-Id
        </span>
        로 전달됩니다.
      </p>

      {message ? (
        <p
          role="alert"
          style={{
            margin: 0,
            fontSize: 12.5,
            color: "var(--color-accent-700)",
          }}
        >
          {message}
        </p>
      ) : null}

      <div className="dialog-actions">
        <button
          type="button"
          className="btn btn-secondary"
          disabled={pending}
          onClick={onClose}
        >
          취소
        </button>
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? "저장 중..." : "저장"}
        </button>
      </div>
    </Dialog>
  );
}

function TextField({
  id,
  name,
  label,
  value,
  onChange,
  placeholder,
  hint,
  error,
  mono = false,
  autoFocus = false,
  disabled,
}: {
  id: string;
  name: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  hint?: string;
  error?: string;
  mono?: boolean;
  autoFocus?: boolean;
  disabled: boolean;
}) {
  return (
    <div className="field">
      <label htmlFor={id}>
        {label} <span style={{ color: "var(--color-accent)" }}>*</span>
      </label>
      <input
        id={id}
        name={name}
        className="input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        spellCheck={mono ? false : undefined}
        disabled={disabled}
        autoFocus={autoFocus}
        aria-invalid={error !== undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        style={{
          ...(mono ? { fontFamily: "var(--mono)", fontSize: 13 } : {}),
          ...(error
            ? {
                borderColor: "var(--color-accent)",
                background: "var(--color-accent-100)",
              }
            : {}),
        }}
      />
      {error ? (
        <p
          id={`${id}-error`}
          style={{
            margin: "6px 0 0",
            fontSize: 12.5,
            color: "var(--color-accent-700)",
          }}
        >
          {error}
        </p>
      ) : null}
      {hint ? (
        <p className="screen-note" style={{ marginTop: 5 }}>
          {hint}
        </p>
      ) : null}
    </div>
  );
}
