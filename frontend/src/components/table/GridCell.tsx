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
