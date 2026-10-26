import type { MeterItem } from "@/lib/meter-items";

type GroupedLine = { label: string; dimensionValues: Record<string, unknown> };

export type MeterLineRow<Line> = {
  kind: "line";
  key: string;
  line: Line;
  isNested: boolean;
};

export type MeterSubtotalRow<Line> = {
  kind: "meter";
  key: string;
  isCollapsedByDefault: true;
  label: string;
  labelTooltip?: string;
  combinationCount: number;
  quantity: string;
  amount: string | null;
  children: MeterLineRow<Line>[];
};

export type MeterRow<Line> = MeterLineRow<Line> | MeterSubtotalRow<Line>;

export function toMeterRows<Line extends GroupedLine>(
  groupKey: string,
  items: MeterItem<Line>[],
): MeterRow<Line>[] {
  return items.map((item): MeterRow<Line> =>
    item.kind === "line"
      ? {
          kind: "line",
          key: `${groupKey}/${item.line.label} ${JSON.stringify(item.line.dimensionValues)}`,
          line: item.line,
          isNested: false,
        }
      : {
          kind: "meter",
          key: item.id,
          isCollapsedByDefault: true,
          label: item.label,
          labelTooltip: item.labelTooltip,
          combinationCount: item.lines.length,
          quantity: item.quantity,
          amount: item.amount,
          children: item.lines.map((line) => ({
            kind: "line",
            key: `${item.id}/${JSON.stringify(line.dimensionValues)}`,
            line,
            isNested: true,
          })),
        },
  );
}
