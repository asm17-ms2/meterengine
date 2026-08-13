const ROWS = [1, 2, 3, 4, 5, 6, 7, 8];

/** 표를 불러오는 동안 자리를 지킨다. 화면 제목과 필터 행은 이미 그려진 상태다. */
export function TableSkeleton() {
  return (
    <div className="skeleton" aria-hidden>
      {ROWS.map((row) => (
        <div key={row} className="skeleton__row">
          <div className="skeleton__bar" />
          <div className="skeleton__bar skeleton__bar--alt" />
          <div className="skeleton__bar" />
          <div className="skeleton__bar skeleton__bar--alt" />
        </div>
      ))}
      <span className="skeleton__label">불러오는 중...</span>
    </div>
  );
}
