export function GridHead({
  columns,
  labels,
}: {
  columns: string;
  /** 오른쪽 정렬할 열은 { label, right: true }로 준다. */
  labels: readonly (string | { label: string; right?: boolean })[];
}) {
  return (
    <div className="grid-head" style={{ gridTemplateColumns: columns }}>
      {labels.map((headLabel, index) => {
        const { label, right } =
          typeof headLabel === "string" ? { label: headLabel, right: false } : headLabel;
        return (
          <div
            key={index}
            className={right ? "grid-cell grid-cell--right" : "grid-cell"}
          >
            {label}
          </div>
        );
      })}
    </div>
  );
}
