import Link from "next/link";

export function EmptyState({
  title,
  body,
  resetHref,
  resetLabel = "필터 초기화",
}: {
  title: string;
  body: string;
  resetHref: string;
  resetLabel?: string;
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
        {resetLabel}
      </Link>
    </div>
  );
}
