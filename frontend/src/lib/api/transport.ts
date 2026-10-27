import "server-only";

import { config } from "@/lib/config";

export type FieldError = {
  field: string;
  message: string;
};

export type ApiError = {
  status: number;
  code: string;
  message: string;
  errors?: FieldError[];
  retryAfterSeconds?: number;
};

export type Result<T> =
  | { ok: true; data: T }
  | { ok: false; error: ApiError };

export function toDisplayMessage(error: ApiError): string {
  if (error.errors && error.errors.length > 0) {
    return error.errors.map((fieldError) => fieldError.message).join(" / ");
  }
  return error.message;
}

const TIMEOUT_MS = 5_000;

type ErrorBody = {
  code?: unknown;
  message?: unknown;
  errors?: unknown;
};

function toFieldErrors(raw: unknown): FieldError[] | undefined {
  if (!Array.isArray(raw)) return undefined;
  const errors = raw.filter(
    (item): item is FieldError =>
      typeof item === "object" &&
      item !== null &&
      typeof (item as FieldError).field === "string" &&
      typeof (item as FieldError).message === "string",
  );
  return errors.length > 0 ? errors : undefined;
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ErrorBody = {};
  try {
    body = (await response.json()) as ErrorBody;
  } catch {
  }
  return {
    status: response.status,
    code: typeof body.code === "string" ? body.code : "http_error",
    message:
      typeof body.message === "string"
        ? body.message
        : "서버가 오류 응답 형식이 아닌 응답을 보냈습니다.",
    errors: toFieldErrors(body.errors),
  };
}

async function readJson<T>(response: Response, isEmptyResponse: boolean): Promise<Result<T>> {
  try {
    const body = await response.text();
    if (!body.trim() && isEmptyResponse) return { ok: true, data: undefined as T };
    return { ok: true, data: JSON.parse(body) as T };
  } catch {
    return {
      ok: false,
      error: {
        status: response.status,
        code: "malformed_response",
        message: "응답을 읽지 못했습니다. 서버가 보낸 응답이 JSON 형식이 아닙니다.",
      },
    };
  }
}

export async function requestApi<T>(
  path: string,
  options: { method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; token?: string; timeoutMs?: number; idempotencyKey?: string; isEmptyResponse?: boolean; clientIp?: string; ifMatch?: string; organizationId?: string } = {},
): Promise<Result<T>> {
  let response: Response;
  try {
    response = await fetch(new URL(path, config.apiBaseUrl), {
      method: options.method ?? "GET",
      headers: {
        Accept: "application/json",
        ...(options.organizationId ? { "X-MeterEngine-Organization-Id": options.organizationId } : {}),
            ...(options.ifMatch ? { "If-Match": options.ifMatch } : {}),
        ...(options.idempotencyKey ? { "Idempotency-Key": options.idempotencyKey } : {}),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
        ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(options.timeoutMs ?? TIMEOUT_MS),
    });
  } catch {
    return { ok: false, error: { status: 0, code: "network_error", message: "서버에 연결하지 못했습니다. 잠시 후 다시 시도하세요." } };
  }
  if (!response.ok) return { ok: false, error: await toApiError(response) };
  if (response.status === 204) return { ok: true, data: undefined as T };
  return readJson<T>(response, options.isEmptyResponse ?? false);
}
