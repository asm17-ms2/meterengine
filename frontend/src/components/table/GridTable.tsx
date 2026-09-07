/**
 * 그리드 표 원시 요소.
 *
 * 디자인이 <table>이 아니라 CSS grid를 쓴다. 헤더와 본문 행이 같은
 * grid-template-columns를 공유해야 열이 맞으므로, 열 정의를 화면마다 상수로
 * 두고 GridHead, GridRow, GridRowButton에 넘긴다.
 */

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
