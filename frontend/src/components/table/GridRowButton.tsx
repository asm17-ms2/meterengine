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
