import { GridCell } from "@/components/table/GridCell";
import { toDimensionValueSlots } from "@/lib/format";

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
  return (
    <GridCell className={className ? `grid-cell--mono ${className}` : "grid-cell--mono"}>
      <div
        className="grid-cell__slots"
        style={{ gridTemplateColumns: `repeat(${slotCount}, minmax(0, 1fr))` }}
      >
        {toDimensionValueSlots(dimensionValues).map((slot) => (
          <span key={slot.hint} className="grid-cell__slot hover-hint" data-hint={slot.hint}>
            <span
              className={
                slot.isMissing
                  ? "grid-cell__slot-value grid-cell--truncate grid-cell--muted"
                  : "grid-cell__slot-value grid-cell--truncate"
              }
            >
              {slot.value}
            </span>
          </span>
        ))}
      </div>
      {children}
    </GridCell>
  );
}
