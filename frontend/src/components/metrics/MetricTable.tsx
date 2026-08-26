import type { MetricRowView } from "@/app/(console)/metrics/state";
import { GridCell, GridHead, GridRow, GridTable } from "@/components/table/Grid";

const COLUMNS = "190px minmax(0, 1fr) 170px 110px 220px";
const MIN_WIDTH = 860;

const HEAD = [
  "코드",
  "이름",
  "이벤트 타입",
  "집계 함수",
  "집계 대상 속성",
] as const;

export function MetricTable({ rows }: { rows: MetricRowView[] }) {
  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD} />
      {rows.map((row) => (
        <GridRow key={row.code} columns={COLUMNS}>
          <GridCell className="grid-cell--mono grid-cell--strong grid-cell--truncate">
            {row.code}
          </GridCell>
          <GridCell className="grid-cell--truncate">{row.name}</GridCell>
          <GridCell className="grid-cell--mono grid-cell--muted grid-cell--truncate">
            {row.eventType}
          </GridCell>
          <GridCell>{row.aggregation}</GridCell>
          <GridCell className="grid-cell--mono grid-cell--muted grid-cell--truncate">
            {row.targetProperty}
          </GridCell>
        </GridRow>
      ))}
    </GridTable>
  );
}
