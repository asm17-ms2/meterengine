/** 필터 행. 아래쪽 2px 구분선이 표와 필터를 가른다. */
export function FilterBar({ children }: { children: React.ReactNode }) {
  return <div className="filter-bar">{children}</div>;
}

/**
 * 필터 행 오른쪽의 시각 표시.
 *
 * 라벨이 '집계 기준'이 아니라 '조회 시각'인 이유: 백엔드가 집계를 미리 만들어 두지
 * 않고 조회 시점에 계산하므로 집계 시각이라는 값 자체가 응답에 없다. 여기 찍히는
 * 것은 이 화면을 서버가 렌더링한 시각이다.
 */
export function QueryStamp({ text }: { text: string }) {
  return <span className="filter-bar__stamp">조회 시각 {text}</span>;
}
