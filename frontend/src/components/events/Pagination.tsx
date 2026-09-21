import Link from "next/link";

const WINDOW = 3;

type PageItem = number | "gap";

export function buildPageItems(currentPage: number, pageCount: number): PageItem[] {
  const start = Math.min(Math.max(currentPage - 1, 1), Math.max(pageCount - WINDOW + 1, 1));
  const pagesAroundCurrent = Array.from({ length: WINDOW }, (_, index) => start + index).filter(
    (page) => page <= pageCount,
  );

  const wantedPages = [...new Set([1, ...pagesAroundCurrent, pageCount])].sort((a, b) => a - b);

  const items: PageItem[] = [];
  for (const page of wantedPages) {
    const previous = items[items.length - 1];
    if (typeof previous === "number") {
      if (page - previous === 2) items.push(previous + 1);
      else if (page - previous > 2) items.push("gap");
    }
    items.push(page);
  }
  return items;
}

export function Pagination({
  currentPage,
  pageCount,
  buildHref,
}: {
  currentPage: number;
  pageCount: number;
  buildHref: (page: number) => string;
}) {
  const items = buildPageItems(currentPage, pageCount);

  return (
    <nav className="pager" aria-label="페이지">
      <StepLink
        href={buildHref(currentPage - 1)}
        disabled={currentPage <= 1}
        label="이전"
      />
      {items.map((item, index) =>
        item === "gap" ? (
          <span key={`gap-${index}`} className="pager__gap" aria-hidden>
            ...
          </span>
        ) : (
          <Link
            key={item}
            className={item === currentPage ? "btn btn-primary" : "btn btn-secondary"}
            style={{ padding: "6px 12px" }}
            href={buildHref(item)}
            aria-current={item === currentPage ? "page" : undefined}
          >
            {item}
          </Link>
        ),
      )}
      <StepLink
        href={buildHref(currentPage + 1)}
        disabled={currentPage >= pageCount}
        label="다음"
      />
    </nav>
  );
}

function StepLink({
  href,
  disabled,
  label,
}: {
  href: string;
  disabled: boolean;
  label: string;
}) {
  if (disabled) {
    return (
      <button
        type="button"
        className="btn btn-secondary"
        style={{ padding: "6px 10px" }}
        disabled
      >
        {label}
      </button>
    );
  }
  return (
    <Link className="btn btn-secondary" style={{ padding: "6px 10px" }} href={href}>
      {label}
    </Link>
  );
}
