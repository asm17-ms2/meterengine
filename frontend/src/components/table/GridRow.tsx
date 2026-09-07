export function GridRow({
  columns,
  className,
  children,
}: {
  columns: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={className ? `grid-row ${className}` : "grid-row"}
      style={{ gridTemplateColumns: columns }}
    >
      {children}
    </div>
  );
}
