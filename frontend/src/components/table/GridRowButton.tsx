/**
 * 클릭 가능한 행. 키보드로도 열려야 해서 <div> 대신 <button>이다.
 * grid-row--clickable이 버튼 기본 스타일을 지운다.
 */
export function GridRowButton({
  columns,
  onClick,
  children,
}: {
  columns: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
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
