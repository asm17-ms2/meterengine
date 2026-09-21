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
