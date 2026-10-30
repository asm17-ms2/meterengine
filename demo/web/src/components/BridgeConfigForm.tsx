"use client";

import { Alert, Button, Input, Space, Typography } from "antd";
import { startTransition, useActionState, type FormEvent } from "react";

import {
  restartBridgeAction,
  updateBridgeConfigAction,
  type BridgeRestartState,
  type BridgeSaveState,
} from "@/app/bridge/actions";
import type { BridgeField } from "@/lib/bridge-form";

const INITIAL_SAVE_STATE: BridgeSaveState = { status: "idle" };
const INITIAL_RESTART_STATE: BridgeRestartState = { status: "idle" };

const NOT_LAUNCHD_MESSAGE = "launchd로 띄운 브리지가 아니라 재시작하지 않았습니다. 직접 껐다 켜세요.";

const FIELD_HELP: Record<BridgeField["kind"], string | undefined> = {
  text: undefined,
  list: "쉼표로 구분합니다",
  number: "0보다 큰 수",
  secret: "비우면 지금 값을 그대로 둡니다",
};

type BridgeConfigFormProps = {
  fields: BridgeField[];
  loadError?: string;
};

function errorFieldOf(state: BridgeSaveState): string | null {
  if (state.status === "invalid") {
    return state.field;
  }
  return state.status === "rejected" ? state.field : null;
}

function FieldInput({ field, hasError }: { field: BridgeField; hasError: boolean }) {
  const status = hasError ? "error" : undefined;
  if (field.kind === "secret") {
    return (
      <Input.Password
        id={field.name}
        name={field.name}
        autoComplete="new-password"
        placeholder={field.placeholder ?? "설정 안 됨"}
        status={status}
      />
    );
  }
  return <Input id={field.name} name={field.name} defaultValue={field.value} status={status} />;
}

function RestartResult({ state }: { state: BridgeRestartState }) {
  switch (state.status) {
    case "idle":
      return null;
    case "restarting":
      return <Alert type="success" title="재시작을 요청했습니다. launchd가 브리지를 다시 띄웁니다." />;
    case "not_launchd":
      return <Alert type="warning" title={NOT_LAUNCHD_MESSAGE} />;
    case "rejected":
      return <Alert type="error" title={`브리지 응답 ${state.http_status}: ${state.error}`} />;
    case "failed":
      return <Alert type="error" title={state.message} />;
  }
}

function SaveResult({ state, onRestart, isRestarting }: { state: BridgeSaveState; onRestart: () => void; isRestarting: boolean }) {
  switch (state.status) {
    case "idle":
      return null;
    case "saved":
      return (
        <Alert
          type="success"
          title={state.restart_required ? "저장했습니다. 재시작이 필요합니다." : "저장했습니다."}
          action={
            state.restart_required ? (
              <Button onClick={onRestart} loading={isRestarting}>
                재시작
              </Button>
            ) : undefined
          }
        />
      );
    case "invalid":
      return <Alert type="error" title={state.message} />;
    case "rejected":
      return <Alert type="error" title={`브리지 응답 ${state.http_status}: ${state.error}`} />;
    case "failed":
      return <Alert type="error" title={state.message} />;
  }
}

export function BridgeConfigForm({ fields, loadError }: BridgeConfigFormProps) {
  const [saveState, saveAction, isSaving] = useActionState(updateBridgeConfigAction, INITIAL_SAVE_STATE);
  const [restartState, restartAction, isRestarting] = useActionState(restartBridgeAction, INITIAL_RESTART_STATE);
  const errorField = errorFieldOf(saveState);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    startTransition(() => saveAction(formData));
  }

  function restart() {
    startTransition(() => restartAction());
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: "100%", maxWidth: 640 }}>
      <Typography.Title level={3}>브리지 설정</Typography.Title>
      {loadError === undefined ? null : <Alert type="error" title="브리지 설정을 읽지 못했습니다" description={loadError} />}
      {fields.length === 0 ? null : (
        <form onSubmit={submit}>
          <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
            {fields.map((field) => (
              <div key={field.name}>
                <label htmlFor={field.name}>
                  <Typography.Text strong>{field.name}</Typography.Text>
                </label>
                <FieldInput field={field} hasError={errorField === field.name} />
                {FIELD_HELP[field.kind] === undefined ? null : (
                  <Typography.Text type="secondary">{FIELD_HELP[field.kind]}</Typography.Text>
                )}
              </div>
            ))}
            <Button type="primary" htmlType="submit" loading={isSaving}>
              저장
            </Button>
          </Space>
        </form>
      )}
      <SaveResult state={saveState} onRestart={restart} isRestarting={isRestarting} />
      <RestartResult state={restartState} />
    </Space>
  );
}
