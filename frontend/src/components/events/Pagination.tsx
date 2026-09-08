import Link from "next/link";

/**
 * 번호 페이지네이션. 링크라서 클릭하면 서버가 화면을 다시 그린다.
 *
 * 커서가 아니라 오프셋인 이유: 디자인이 마지막 페이지 번호(... 24)와 총 건수를
 * 함께 요구한다. 둘 다 전체 건수를 알아야 나오는 값이라 커서로는 만들 수 없다.
 * 백엔드도 total을 내려주는 쪽으로 갔다 (MS2-131).
 */

/** 가운데 창을 세 칸으로 고정한다. 디자인의 `1 2 3 ... 24`가 그 모양이다. */
const WINDOW = 3;

type PageItem = number | "gap";

/**
 * 그릴 페이지 번호와 축약 위치를 정한다. 1부터 센다 (화면 번호).
 *
 * 항상 첫 페이지, 마지막 페이지, 현재 페이지 주변 세 칸을 넣는다. 사이가 두 칸
 * 벌어지면 빠진 번호를 그냥 채운다. 한 칸 자리에 '...'을 넣으면 원래 번호보다
 * 길어지면서 누르지도 못하기 때문이다.
 */
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
  /** 화면 번호. 1부터 센다. 쿼리스트링의 page는 0부터라 페이지가 변환해 넘긴다. */
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

/**
 * 이전/다음. 끝에 닿으면 링크가 아니라 disabled 버튼이 된다. <a>는 비활성화할 수
 * 없어서, 링크로 두고 클릭만 막으면 키보드 사용자에게는 여전히 눌리는 것으로 보인다.
 */
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
