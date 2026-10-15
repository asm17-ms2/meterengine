export type MeterItem<Line> =
  | { kind: "line"; line: Line }
  | {
      kind: "meter";
      id: string;
      label: string;
      labelTooltip?: string;
      quantity: string;
      amount: string | null;
      lines: Line[];
    };

/** 같은 미터의 줄이 둘 이상 붙어 있으면 소계 줄 아래로 묶는다. */
export function toMeterItems<Row, Line extends { label: string; labelTooltip?: string }>(
  groupId: string,
  rows: Row[],
  meterKeyOf: (row: Row) => string,
  toLine: (row: Row) => Line,
  subtotalOf: (rows: Row[]) => { quantity: string; amount: string | null },
): MeterItem<Line>[] {
  const items: MeterItem<Line>[] = [];
  let start = 0;
  while (start < rows.length) {
    let end = start + 1;
    while (end < rows.length && meterKeyOf(rows[end]) === meterKeyOf(rows[start])) end++;
    const run = rows.slice(start, end);
    const lines = run.map(toLine);
    items.push(
      run.length === 1
        ? { kind: "line", line: lines[0] }
        : {
            kind: "meter",
            id: `${groupId}/${meterKeyOf(run[0])}`,
            label: lines[0].label,
            labelTooltip: lines[0].labelTooltip,
            ...subtotalOf(run),
            lines,
          },
    );
    start = end;
  }
  return items;
}

export function linesOf<Line>(items: MeterItem<Line>[]): Line[] {
  return items.flatMap((item) => (item.kind === "line" ? [item.line] : item.lines));
}

export function sumOf(values: number[]): number {
  return values.reduce((sum, value) => sum + value, 0);
}
