import Link from "next/link";

import { RetryButton } from "@/components/screen/RetryButton";
import type { ApiError } from "@/lib/api/client";

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
  const detail = [error.title, error.detail].filter(Boolean).join(" - ");
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
