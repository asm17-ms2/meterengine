import { CommanderError } from "commander";
import { MeterEngineApiError, MeterEngineConnectionError, MeterEngineTimeoutError } from "@meterengine/sdk";

export const EXIT_OK = 0;
// 서버가 거절해 입력을 고쳐야 하는 실패. 429를 뺀 4xx.
export const EXIT_FAILED = 1;
// 명령이나 설정이 틀려 CLI가 서버에 보내기 전에 거절했다. bash 내장 명령의 관례를 따른다.
export const EXIT_USAGE = 2;
// 같은 명령을 다시 실행하면 될 수 있는 실패. 가이드의 재전송 기준(integration/retries)과 같다.
export const EXIT_RETRYABLE = 3;

export class UsageError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "UsageError";
    this.code = code;
  }
}

export interface ErrorOutput {
  code: string;
  message: string;
  errors?: unknown;
}

export function exitCodeFor(error: unknown): number {
  if (error instanceof CommanderError) return error.exitCode === 0 ? EXIT_OK : EXIT_USAGE;
  if (error instanceof UsageError) return EXIT_USAGE;
  if (error instanceof MeterEngineApiError) return error.status === 429 || error.status >= 500 ? EXIT_RETRYABLE : EXIT_FAILED;
  if (error instanceof MeterEngineConnectionError || error instanceof MeterEngineTimeoutError) return EXIT_RETRYABLE;
  return EXIT_FAILED;
}

export function errorOutputFor(error: unknown): ErrorOutput {
  if (error instanceof MeterEngineApiError) {
    return error.errors === undefined ? { code: error.code, message: error.message } : { code: error.code, message: error.message, errors: error.errors };
  }
  if (error instanceof UsageError) return { code: error.code, message: error.message };
  if (error instanceof CommanderError) {
    const message = error.code === "commander.help" ? "하위 명령을 함께 쓰세요. 목록은 도움말에 있습니다" : error.message.replace(/^error: /, "");
    return { code: "invalid_usage", message };
  }
  if (error instanceof MeterEngineConnectionError) return { code: "connection_failed", message: error.message };
  if (error instanceof MeterEngineTimeoutError) return { code: "timeout", message: error.message };
  return { code: "unexpected_error", message: error instanceof Error ? error.message : String(error) };
}
