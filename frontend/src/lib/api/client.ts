import "server-only";

import { config } from "@/lib/config";

/**
 * 백엔드 호출 한 곳.
 *
 * 브라우저가 아니라 Next 서버(Node)에서 나가는 요청이다. 그래서 CORS가 없고,
 * X-Organization-Id를 주입하는 지점이 여기 하나뿐이며, 조직 식별자가
 * 클라이언트 번들에 들어가지 않는다.
 */

/**
 * problem detail의 errors 한 칸. 백엔드 ProblemFieldError와 1:1이다.
 *
 * 백엔드 계약에서 도입사가 읽는 한국어는 여기 message 한 자리뿐이다. 나머지
 * (title, detail)는 영어이고 로그와 개발자용이다 (backend/README.md "오류 응답").
 */
export type FieldError = {
  /** 도입사가 보낸 와이어 이름이다. JSON 키, 쿼리 파라미터, 헤더명이고 자바 필드명이 아니다. */
  field: string;
  /** 한국어. 화면에 그대로 띄울 수 있는 유일한 서버 문구다. */
  message: string;
};

/** 백엔드가 내려주는 RFC 9457 problem+json을 화면이 쓸 형태로 줄인 것. */
export type ApiError = {
  /** HTTP 상태. 네트워크 실패나 타임아웃이면 0. */
  status: number;
  /**
   * 백엔드가 problem detail에 얹는 커스텀 확장 멤버.
   *
   * 백엔드가 내는 값 (2026-08-24, MS2-157 기준). 정본은 백엔드 ErrorCodes다:
   *   validation_error / unknown_customer_reference / invalid_event /
   *   malformed_request_body / request_type_not_supported /
   *   response_type_not_acceptable / method_not_allowed / endpoint_not_found /
   *   customer_not_found / customer_has_events / unknown_organization /
   *   metric_not_found / price_policy_already_exists / invalid_price_policy
   *
   * 여기서 만든 값: network_error / http_error / malformed_response / dev_forced.
   *
   * 닫힌 집합처럼 보이지만 열린 것으로 다룬다. 백엔드가 code를 추가해도 이 주석은
   * 따라오지 않고, 5xx와 본문이 problem+json이 아닌 응답에는 code가 없어
   * http_error로 채워진다. 모르는 값은 기본 문구로 떨어뜨린다 (MS2-150 B-2).
   */
  code: string;
  title: string;
  detail: string;
  /**
   * 어느 값이 왜 틀렸는지. code가 validation_error일 때만 실린다.
   *
   * 우리가 만든 오류(network_error 등)에는 없다. 화면은 있으면 쓰고 없으면 code로
   * 고른 기본 문구를 쓴다.
   */
  errors?: FieldError[];
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
  errors?: unknown;
};

/**
 * errors 배열을 원소 단위로 거른다.
 *
 * 배열 통째로 믿지 않는 이유: openapi.yaml의 ProblemFieldError에 required가 없어
 * field나 message가 빠진 원소가 계약상 가능하다. 한 칸이 이상하다고 나머지를 버릴
 * 이유도 없어서 성한 것만 남긴다. 남는 게 없으면 없는 것으로 친다 - 화면은
 * errors가 비었는지 없는지를 구별하지 않는다.
 */
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
    errors: toFieldErrors(problem.errors),
  };
}

/**
 * 2xx 본문을 읽는다. 파싱 실패도 던지지 않고 Result의 오류로 바꾼다.
 *
 * 200인데 본문이 JSON이 아닐 수 있다. 리버스 프록시나 게이트웨이가 끼어들어 HTML을
 * 돌려주는 경우다. 여기서 던지면 serverFetch가 선언한 "던지지 않는다"가 깨지고 Next가
 * 라우트를 error.tsx로 갈아치운다 (MS2-152). 실패 경로의 toApiError는 같은 방어를
 * 처음부터 갖고 있었고, 이건 성공 경로에 같은 것을 두는 것이다.
 *
 * status에 실제 상태(대개 200)를 그대로 넣는다. '200인데 실패'가 정확한 사실이고
 * 문의를 받을 때 진단에 쓰인다. status 0은 HTTP 응답 자체가 없었다는 뜻이라 여기엔
 * 맞지 않는다.
 */
async function readJson<T>(response: Response): Promise<Result<T>> {
  try {
    return { ok: true, data: (await response.json()) as T };
  } catch {
    return {
      ok: false,
      error: {
        status: response.status,
        code: "malformed_response",
        title: "응답을 읽지 못했습니다",
        detail: "서버가 보낸 응답이 JSON 형식이 아닙니다.",
      },
    };
  }
}

/**
 * 조회와 쓰기가 공유하는 네트워크 계층. 헤더 주입, 타임아웃, problem+json 변환이
 * 여기 한 곳에 있다.
 *
 * 응답 본문을 읽지 않고 Response를 그대로 넘긴다. 조회는 항상 JSON이지만 쓰기는
 * 204(본문 없음)가 섞여서, 본문을 어떻게 읽을지는 호출부가 정해야 한다.
 */
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
        Accept: "application/json, application/problem+json",
        // 본문이 있을 때만 붙인다. DELETE에 Content-Type을 달면 본문 없는 요청에
        // 형식을 선언하는 꼴이라 서버가 415로 되받을 여지가 생긴다.
        ...(init.body === undefined
          ? {}
          : { "Content-Type": "application/json" }),
      },
      body: init.body,
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
  return { ok: true, data: response };
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

  const result = await call(url, { method: "GET" });
  if (!result.ok) return result;
  return readJson<T>(result.data);
}

/**
 * 쓰기(등록, 수정, 삭제). 조회와 같은 Result 규약이라 실패해도 던지지 않는다.
 *
 * 브라우저가 이 함수에 직접 닿지 않는다. 호출자는 Server Action이고, 거기서
 * 조직 헤더가 붙는다 (MS2-154). 브라우저가 백엔드를 직접 부르면 커스텀 헤더라
 * preflight에 막히고, 조직 식별자가 devtools에 노출된다.
 *
 * 204는 {@code data}가 undefined다. 타입 인자로 그걸 표현할 방법이 없어서
 * DELETE 호출부는 {@code serverSend<void>}로 부르고 data를 보지 않는다.
 */
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

  // 204(삭제)는 본문이 없는 것이 정상이라 파싱 자체를 건너뛴다. 나머지 2xx의 빈
  // 본문이나 비JSON은 readJson이 오류로 바꾼다.
  if (result.data.status === 204) return { ok: true, data: undefined as T };
  return readJson<T>(result.data);
}
