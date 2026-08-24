import Link from "next/link";

import { RetryButton } from "@/components/screen/RetryButton";
import type { ApiError } from "@/lib/api/client";

/**
 * 본문 문구를 code로 고른다.
 *
 * 백엔드 계약이 title과 detail을 영어로 두고 그대로 띄우지 말라고 못박았다
 * (backend/README.md "오류 응답"). 둘을 이어 붙이던 예전 코드는 화면에
 * "Bad Request - the request could not be accepted as sent"를 띄웠다 (MS2-152).
 *
 * 조회 화면에서 닿을 수 있는 code만 둔다. 쓰기에서만 나는 것(customer_has_events
 * 등)은 customers/actions.ts가 따로 다룬다. code 집합은 닫혀 있지 않아서 모르는
 * 값은 default로 떨어지는데, 5xx가 그리로 온다 - 백엔드가 5xx의 본문 형식을
 * 약속하지 않으므로 클라이언트가 기본 문구를 갖고 있어야 한다.
 */
function bodyMessage(error: ApiError): string {
  switch (error.code) {
    // 우리가 만든 code는 title과 detail이 둘 다 한국어다 (client.ts의 "여기서 만든
    // 값"). 계약이 막는 것은 백엔드가 준 영어 문구이지 이 자리가 아니라서 둘 다 쓴다.
    case "network_error":
    case "malformed_response":
    case "dev_forced":
      return [error.title, error.detail].filter(Boolean).join(" - ");
    // 서버가 어느 값이 왜 틀렸는지 짚어준 유일한 자리다. 여러 칸이 틀렸으면 전부
    // 보여준다 - 하나만 고쳐 다시 눌렀다가 또 막히는 것보다 낫다.
    case "validation_error":
      return error.errors?.length
        ? error.errors.map((it) => it.message).join(" / ")
        : "조회 조건을 확인해주세요.";
    case "unknown_organization":
      return "도입사를 찾을 수 없습니다. 설정을 확인해주세요.";
    case "endpoint_not_found":
      return "요청한 주소를 찾을 수 없습니다.";
    default:
      return "잠시 후 다시 시도해주세요.";
  }
}

/**
 * 데이터를 못 불러왔을 때. 필터 행 아래 제자리에 들어간다.
 *
 * error.tsx가 아니라 이 컴포넌트를 쓰는 이유: error.tsx는 라우트 세그먼트를 통째로
 * 갈아치워서 화면 제목과 필터 행까지 사라진다. 디자인은 그 둘이 남아 있어야 한다.
 */
export function ErrorState({
  title,
  error,
  narrowerHref,
}: {
  title: string;
  error: ApiError;
  /**
   * '기간 좁히기'가 갈 곳. 보통 직전 달이다.
   *
   * 없으면 그 버튼이 빠진다. 기간으로 조회하지 않는 화면(고객 목록)에서는
   * 누를 데가 없어서다 - 이 도입사의 고객 전부가 응답이라 좁힐 조건이 없다.
   */
  narrowerHref?: string;
}) {
  const detail = bodyMessage(error);
  const status = error.status > 0 ? ` (${error.status})` : "";

  return (
    <div className="error-state">
      <div className="error-state__title">{title}</div>
      <p className="error-state__body">
        {detail}
        {status}
      </p>
      <div className="error-state__actions">
        <RetryButton />
        {narrowerHref ? (
          <Link className="btn btn-secondary" href={narrowerHref}>
            기간 좁히기
          </Link>
        ) : null}
      </div>
    </div>
  );
}
