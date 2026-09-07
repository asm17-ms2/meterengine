/** 필터 행. 아래쪽 2px 구분선이 표와 필터를 가른다. */
export function FilterBar({ children }: { children: React.ReactNode }) {
  return <div className="filter-bar">{children}</div>;
}
