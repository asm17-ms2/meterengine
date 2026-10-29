import "server-only";

import {
  MeterEngineApiError,
  MeterEngineBatchError,
  MeterEngineClient,
  MeterEngineConnectionError,
  MeterEngineTimeoutError,
  type FieldError,
  type MeterEngineClientOptions,
} from "@meterengine/sdk";

import { parseTargets, type Target } from "@/lib/config";

export type ErrorView = {
  kind: "api" | "connection" | "timeout" | "batch" | "unknown";
  status?: number;
  code?: string;
  message: string;
  errors?: FieldError[];
};

export function listTargets(): Target[] {
  return parseTargets(process.env);
}

export function targetNames(): string[] {
  return listTargets().map((target) => target.name);
}

export type ClientOverrides = Pick<MeterEngineClientOptions, "timeoutMs" | "maxRetries">;

export function clientFor(name: string, overrides: ClientOverrides = {}): MeterEngineClient {
  const target = listTargets().find((candidate) => candidate.name === name);
  if (target === undefined) {
    throw new Error(`설정에 없는 대상입니다: ${name}`);
  }
  return new MeterEngineClient({ ...overrides, baseUrl: target.base_url, apiKey: target.api_key });
}

function messageOf(error: Error): string {
  if (error instanceof AggregateError) {
    const errors: unknown[] = error.errors;
    return errors
      .filter((inner): inner is Error => inner instanceof Error)
      .map((inner) => inner.message)
      .filter((message) => message !== "")
      .join(", ");
  }
  return error.message;
}

function deepestCauseMessage(error: Error): string | undefined {
  let current: unknown = error.cause;
  let detail: string | undefined;
  while (current instanceof Error) {
    const message = messageOf(current);
    if (message !== "") {
      detail = message;
    }
    current = current.cause;
  }
  return detail;
}

function toApiErrorView(error: MeterEngineApiError): ErrorView {
  const view: ErrorView = { kind: "api", status: error.status, code: error.code, message: error.message };
  if (error.errors !== undefined) {
    view.errors = error.errors;
  }
  return view;
}

export function toErrorView(error: unknown): ErrorView {
  if (error instanceof MeterEngineBatchError) {
    const cause = toErrorView(error.cause);
    return { ...cause, kind: "batch", message: `${error.message}: ${cause.message}` };
  }
  if (error instanceof MeterEngineApiError) {
    return toApiErrorView(error);
  }
  if (error instanceof MeterEngineTimeoutError) {
    return { kind: "timeout", message: error.message };
  }
  if (error instanceof MeterEngineConnectionError) {
    const detail = deepestCauseMessage(error);
    return { kind: "connection", message: detail === undefined ? error.message : `${error.message} (${detail})` };
  }
  if (error instanceof Error) {
    return { kind: "unknown", message: error.message };
  }
  return { kind: "unknown", message: String(error) };
}
