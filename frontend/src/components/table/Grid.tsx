/**
 * 그리드 표 원시 요소.
 *
 * 디자인이 <table>이 아니라 CSS grid를 쓴다. 헤더와 본문 행이 같은
 * grid-template-columns를 공유해야 열이 맞으므로, 열 정의를 화면마다 상수로
 * 두고 이 컴포넌트들에 넘긴다.
 */

type Columns = { columns: string };

/** 가로 스크롤 컨테이너. minWidth 아래로는 표가 찌그러지지 않고 스크롤된다. */
export function GridTable({
  minWidth,
  children,
}: {
  minWidth: number;
  children: React.ReactNode;
}) {
  return (
    <div className="grid-table">
      <div style={{ minWidth }}>{children}</div>
    </div>
  );
}

export function GridHead({
  columns,
  labels,
}: Columns & {
  /** 오른쪽 정렬할 열은 { label, right: true }로 준다. */
  labels: readonly (string | { label: string; right?: boolean })[];
}) {
  return (
    <div className="grid-head" style={{ gridTemplateColumns: columns }}>
      {labels.map((entry, i) => {
        const { label, right } =
          typeof entry === "string" ? { label: entry, right: false } : entry;
        return (
          <div
            key={i}
            className={right ? "grid-cell grid-cell--right" : "grid-cell"}
          >
            {label}
          </div>
        );
      })}
    </div>
  );
}

export function GridRow({
  columns,
  className,
  children,
}: Columns & { className?: string; children: React.ReactNode }) {
  return (
    <div
      className={className ? `grid-row ${className}` : "grid-row"}
      style={{ gridTemplateColumns: columns }}
    >
      {children}
    </div>
  );
}

/**
 * 클릭 가능한 행. 키보드로도 열려야 해서 <div> 대신 <button>이다.
 * grid-row--clickable이 버튼 기본 스타일을 지운다.
 */
export function GridRowButton({
  columns,
  onClick,
  children,
}: Columns & { onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      className="grid-row grid-row--clickable"
      style={{ gridTemplateColumns: columns }}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export function GridCell({
  className,
  children,
}: {
  className?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className={className ? `grid-cell ${className}` : "grid-cell"}>
      {children}
    </div>
  );
}
