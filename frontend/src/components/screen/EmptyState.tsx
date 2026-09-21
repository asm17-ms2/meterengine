import Link from "next/link";

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
