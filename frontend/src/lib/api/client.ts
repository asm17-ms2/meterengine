import "server-only";

import { config } from "@/lib/config";

/**
 * 백엔드 호출 한 곳.
 *
 * 브라우저가 아니라 Next 서버(Node)에서 나가는 요청이다. 그래서 CORS가 없고,
 * X-Organization-Id를 주입하는 지점이 여기 하나뿐이며, 조직 식별자가
 * 클라이언트 번들에 들어가지 않는다.
 */

/** 백엔드가 내려주는 RFC 9457 problem+json을 화면이 쓸 형태로 줄인 것. */
export type ApiError = {
  /** HTTP 상태. 네트워크 실패나 타임아웃이면 0. */
  status: number;
  /**
   * 백엔드가 problem detail에 얹는 커스텀 확장 멤버.
   * validation_error / customer_not_found / invalid_event 중 하나이거나,
   * 여기서 만든 network_error / dev_forced.
   */
  code: string;
  title: string;
  detail: string;
};

export type Result<T> =
  | { ok: true; data: T }
  | { ok: false; error: ApiError };

/**
 * 백엔드가 죽었을 때 SSR 스트림이 멈추지 않도록 자른다.
 * 이 시간을 넘기면 화면은 디자인의 에러 블록을 그린다.
 */
const TIMEOUT_MS = 5_000;

type ProblemDetail = {
  code?: unknown;
  title?: unknown;
  detail?: unknown;
};

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {};
  try {
    problem = (await response.json()) as ProblemDetail;
  } catch {
    // problem+json이 아닌 응답(5xx의 기본 에러 페이지 등). 상태 코드만 쓴다.
  }
  return {
    status: response.status,
    code: typeof problem.code === "string" ? problem.code : "http_error",
    title:
      typeof problem.title === "string" ? problem.title : `HTTP ${response.status}`,
    detail: typeof problem.detail === "string" ? problem.detail : "",
  };
}

/**
 * 던지지 않고 Result를 돌려준다. 이게 중요하다. 예외를 던지면 Next가 페이지를
 * error.tsx로 갈아치워 화면 헤더와 필터 행까지 사라지는데, 디자인은 에러 블록이
 * 필터 행 아래 제자리에 있어야 한다.
 */
export async function serverFetch<T>(
  baseUrl: string,
  path: string,
  searchParams?: Record<string, string | undefined>,
): Promise<Result<T>> {
  const url = new URL(path, baseUrl);
  for (const [key, value] of Object.entries(searchParams ?? {})) {
    if (value !== undefined) url.searchParams.set(key, value);
  }

  let response: Response;
  try {
    response = await fetch(url, {
      headers: {
        "X-Organization-Id": config.organizationId,
        Accept: "application/json, application/problem+json",
      },
      // 런타임 기본값과 같지만, 빌드 타임 prerender가 응답을 굳히는 것을 막고
      // 의도를 코드에 남긴다.
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (cause) {
    const timedOut = cause instanceof Error && cause.name === "TimeoutError";
    return {
      ok: false,
      error: {
        status: 0,
        code: "network_error",
        title: timedOut ? "응답 시간 초과" : "서버에 연결하지 못했습니다",
        detail: timedOut
          ? `${TIMEOUT_MS / 1000}초 안에 응답이 오지 않았습니다.`
          : "백엔드가 실행 중인지 확인해주세요.",
      },
    };
  }

  if (!response.ok) return { ok: false, error: await toApiError(response) };
  return { ok: true, data: (await response.json()) as T };
}
