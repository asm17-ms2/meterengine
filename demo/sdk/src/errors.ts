import type { ErrorResponse, FieldError } from "./types.ts";

export class MeterEngineApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: FieldError[] | undefined;
  readonly retryAfterSeconds: number | undefined;

  constructor(status: number, body: ErrorResponse, retryAfterSeconds?: number) {
    super(body.message);
    this.name = "MeterEngineApiError";
    this.status = status;
    this.code = body.code;
    this.errors = body.errors;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export class MeterEngineConnectionError extends Error {
  constructor(cause: unknown) {
    super("MeterEngine 서버에 연결하지 못했습니다", { cause });
    this.name = "MeterEngineConnectionError";
  }
}

export class MeterEngineTimeoutError extends Error {
  readonly timeoutMs: number;

  constructor(timeoutMs: number) {
    super(`MeterEngine 서버가 ${timeoutMs}ms 안에 응답하지 않았습니다`);
    this.name = "MeterEngineTimeoutError";
    this.timeoutMs = timeoutMs;
  }
}

export type WebhookVerificationReason =
  | "missing_header"
  | "invalid_timestamp"
  | "timestamp_out_of_range"
  | "signature_mismatch"
  | "invalid_payload";

const WEBHOOK_VERIFICATION_MESSAGES: Record<WebhookVerificationReason, string> = {
  missing_header: "웹훅 헤더 webhook-id, webhook-timestamp, webhook-signature 중 빠진 것이 있습니다",
  invalid_timestamp: "webhook-timestamp가 유닉스 초 정수가 아닙니다",
  timestamp_out_of_range: "webhook-timestamp가 허용 시각 차를 벗어났습니다",
  signature_mismatch: "맞는 웹훅 서명이 없습니다",
  invalid_payload: "웹훅 본문이 JSON이 아닙니다",
};

export class WebhookVerificationError extends Error {
  readonly reason: WebhookVerificationReason;

  constructor(reason: WebhookVerificationReason) {
    super(WEBHOOK_VERIFICATION_MESSAGES[reason]);
    this.name = "WebhookVerificationError";
    this.reason = reason;
  }
}
