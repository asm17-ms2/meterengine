import { GridCell } from "@/components/table/GridCell";
import { formatDimensionValuePairs } from "@/lib/format";

export function countDimensionSlots(dimensionValuesList: Record<string, unknown>[]): number {
  return Math.max(
    1,
    ...dimensionValuesList.map((dimensionValues) => Object.keys(dimensionValues).length),
  );
}

export function DimensionValuesCell({
  dimensionValues,
  slotCount,
  className,
  children,
}: {
  dimensionValues: Record<string, unknown>;
  slotCount: number;
  className?: string;
  children?: React.ReactNode;
}) {
  const pairs = formatDimensionValuePairs(dimensionValues);
  return (
    <GridCell className={className ? `grid-cell--mono ${className}` : "grid-cell--mono"}>
      <div
        className="grid-cell__slots"
        style={{ gridTemplateColumns: `repeat(${slotCount}, minmax(0, 1fr))` }}
        title={pairs.length === 0 ? undefined : pairs.join(", ")}
      >
        {pairs.map((pair) => (
          <span key={pair} className="grid-cell--truncate">
            {pair}
          </span>
        ))}
      </div>
      {children}
    </GridCell>
  );
}
