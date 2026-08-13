import { mockEventPage } from "@/lib/api/events.mock";
import { PAGE_SIZE } from "@/lib/api/events";
import { currentMonth } from "@/lib/month";

/**
 * GET /v1/events(MS2-131) 목. 화면이 쓰는 것과 같은 생성기를 HTTP로 노출한다.
 *
 * 화면은 이 라우트를 부르지 않는다. Server Component가 mockEventPage를 직접 부른다.
 * 자기 자신을 HTTP로 부르려면 절대 origin이 필요하고(포트를 추측하거나 headers()를
 * 뒤져야 한다) 왕복만 늘기 때문이다.
 *
 * 그럼에도 이 파일을 두는 이유는 계약을 눈으로 확인할 수 있게 하려는 것이다.
 * MS2-131 담당자가 실제 응답과 나란히 놓고 볼 수 있다.
 *
 *   curl -s 'localhost:3000/api/mock/v1/events?month=2026-08&page=0&size=20' | jq
 *
 * PR #24가 머지되면 .env.local에 METERENGINE_EVENTS_BASE_URL을 채워 실제 백엔드로
 * 바꾸고, 이 파일과 events.mock.ts를 지운다.
 *
 * 실제 백엔드와 다른 점.
 *   - X-Organization-Id를 읽지 않는다. 목에는 도입사가 하나뿐이다.
 *   - customer_id, event_type 필터가 없다. 이 화면의 필터가 기간 하나뿐이라
 *     화면이 보내지 않는 파라미터를 목이 흉내 낼 이유가 없다.
 *   - customer_not_found(400)가 나올 수 없다. customer_id를 받지 않아서다.
 */

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const MAX_SIZE = 100;

/** 백엔드의 problem+json과 같은 모양. code는 UsageEventExceptionHandler의 확장 멤버다. */
function validationError(detail: string): Response {
  return Response.json(
    {
      type: "about:blank",
      title: "Bad Request",
      status: 400,
      detail,
      code: "validation_error",
    },
    { status: 400, headers: { "Content-Type": "application/problem+json" } },
  );
}

export async function GET(request: Request): Promise<Response> {
  const params = new URL(request.url).searchParams;

  const rawMonth = params.get("month");
  if (rawMonth !== null && !MONTH_PATTERN.test(rawMonth)) {
    return validationError("month는 yyyy-MM 형식이어야 합니다.");
  }
  // 백엔드도 month를 생략하면 현재 KST 월로 계산하고 그 값을 응답에 에코한다.
  const month = rawMonth ?? currentMonth();

  const page = Number(params.get("page") ?? "0");
  if (!Number.isInteger(page) || page < 0) {
    return validationError("page는 0 이상의 정수여야 합니다.");
  }

  const size = Number(params.get("size") ?? String(PAGE_SIZE));
  if (!Number.isInteger(size) || size < 1 || size > MAX_SIZE) {
    return validationError(`size는 1 이상 ${MAX_SIZE} 이하의 정수여야 합니다.`);
  }

  return Response.json(mockEventPage({ month, page }, size));
}
