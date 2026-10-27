import { MeterEngineApiError, MeterEngineConnectionError, MeterEngineTimeoutError } from "./errors.ts";

const MAX_BACKOFF_MS = 5000;
const BASE_BACKOFF_MS = 200;
const DELAY_SECONDS_PATTERN = /^\d+$/;
const HTTP_DATE_PATTERN = /^[A-Z][a-z]{2}, \d{2} [A-Z][a-z]{2} \d{4} \d{2}:\d{2}:\d{2} GMT$/;

export function isRetryable(error: unknown): boolean {
  if (error instanceof MeterEngineApiError) {
    return error.status === 429 || error.status >= 500;
  }
  return error instanceof MeterEngineConnectionError || error instanceof MeterEngineTimeoutError;
}

export function parseRetryAfter(header: string | null, nowMs: number): number | undefined {
  if (header === null) {
    return undefined;
  }
  const value = header.trim();
  if (DELAY_SECONDS_PATTERN.test(value)) {
    return Number(value);
  }
  if (!HTTP_DATE_PATTERN.test(value)) {
    return undefined;
  }
  const dateMs = Date.parse(value);
  if (Number.isNaN(dateMs)) {
    return undefined;
  }
  return Math.max(0, Math.ceil((dateMs - nowMs) / 1000));
}

export function retryDelayMs(attempt: number, retryAfterSeconds: number | undefined, random: () => number): number {
  if (retryAfterSeconds !== undefined) {
    return retryAfterSeconds * 1000;
  }
  return random() * Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2 ** attempt);
}
