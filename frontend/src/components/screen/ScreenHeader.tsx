/** 화면 제목과 오른쪽 메타 텍스트 한 줄. */
export function ScreenHeader({
  title,
  children,
}: {
  title: React.ReactNode;
  children?: React.ReactNode;
}) {
  return (
    <div className="screen-header">
      <h3 style={{ margin: 0 }}>{title}</h3>
      {children ? <div className="screen-header__meta">{children}</div> : null}
    </div>
  );
}
