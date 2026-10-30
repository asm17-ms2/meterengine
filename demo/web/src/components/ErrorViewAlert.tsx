"use client";

import { Alert } from "antd";

import type { ErrorView } from "@/lib/meter-engine";

const KIND_LABELS: Record<ErrorView["kind"], string> = {
  api: "API 오류",
  connection: "연결 실패",
  timeout: "시간 초과",
  batch: "묶음 전송 중단",
  unknown: "오류",
};

function errorTitle(error: ErrorView): string {
  const parts = [KIND_LABELS[error.kind], error.status, error.code].filter((part) => part !== undefined);
  return `${parts.join(" ")}: ${error.message}`;
}

export function ErrorViewAlert({ error }: { error: ErrorView }) {
  const fieldErrors = error.errors ?? [];
  return (
    <Alert
      type="error"
      title={errorTitle(error)}
      description={
        fieldErrors.length === 0 ? undefined : (
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            {fieldErrors.map((fieldError, index) => (
              <li key={index}>
                {fieldError.field}: {fieldError.message}
              </li>
            ))}
          </ul>
        )
      }
    />
  );
}
