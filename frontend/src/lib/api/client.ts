import "server-only";

import { config } from "@/lib/config";

export type FieldError = {
  field: string;
  message: string;
};

/** 백엔드가 내려주는 오류 응답을 화면이 쓸 형태로 줄인 것. */
export type ApiError = {
  status: number;
  code: string;
  message: string;
  errors?: FieldError[];
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

async function readJson<T>(response: Response): Promise<Result<T>> {
  try {
    return { ok: true, data: (await response.json()) as T };
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

async function call(
  url: URL,
  init: { method: string; body?: string },
): Promise<Result<Response>> {
  let response: Response;
  try {
    response = await fetch(url, {
      method: init.method,
      headers: {
        "X-Organization-Id": config.organizationId,
        Accept: "application/json",
        ...(init.body === undefined
          ? {}
          : { "Content-Type": "application/json" }),
      },
      body: init.body,
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (cause) {
    const isTimedOut = cause instanceof Error && cause.name === "TimeoutError";
    return {
      ok: false,
      error: {
        status: 0,
        code: "network_error",
        message: isTimedOut
          ? `응답 시간 초과. ${TIMEOUT_MS / 1000}초 안에 응답이 오지 않았습니다.`
          : "서버에 연결하지 못했습니다. 백엔드가 실행 중인지 확인해주세요.",
      },
    };
  }

  if (!response.ok) return { ok: false, error: await toApiError(response) };
  return { ok: true, data: response };
}

export async function serverFetch<T>(
  baseUrl: string,
  path: string,
  searchParams?: Record<string, string | undefined>,
): Promise<Result<T>> {
  const url = new URL(path, baseUrl);
  for (const [key, value] of Object.entries(searchParams ?? {})) {
    if (value !== undefined) url.searchParams.set(key, value);
  }

  const result = await call(url, { method: "GET" });
  if (!result.ok) return result;
  return readJson<T>(result.data);
}

export async function serverSend<T>(
  baseUrl: string,
  path: string,
  options: { method: "POST" | "PUT" | "DELETE"; body?: unknown },
): Promise<Result<T>> {
  const result = await call(new URL(path, baseUrl), {
    method: options.method,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  if (!result.ok) return result;

  if (result.data.status === 204) return { ok: true, data: undefined as T };
  return readJson<T>(result.data);
}
