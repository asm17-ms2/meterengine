import Link from "next/link";

/**
 * 조건에 맞는 데이터가 0건일 때. 표 자리에 들어간다.
 *
 * 디자인의 '필터 초기화' 버튼은 이번 달로 돌아가는 링크다. 지금 필터가 기간
 * 하나뿐이라 그게 곧 초기화다.
 */
export function EmptyState({
  title,
  body,
  resetHref,
}: {
  title: string;
  body: string;
  resetHref: string;
}) {
  return (
    <div className="empty-state">
      <div className="empty-state__title">{title}</div>
      <p className="empty-state__body">{body}</p>
      <Link
        className="btn btn-secondary"
        href={resetHref}
        style={{ marginTop: 4 }}
      >
        필터 초기화
      </Link>
    </div>
  );
}
