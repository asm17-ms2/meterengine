import type { BillableMetricRowView } from "@/app/(console)/billable-metrics/state";
import { GridCell, GridHead, GridRow, GridTable } from "@/components/table/Grid";

const COLUMNS = "190px minmax(0, 1fr) 150px 100px 190px 110px";
const MIN_WIDTH = 940;

const HEAD = [
  "코드",
  "이름",
  "이벤트 타입",
  "집계 함수",
  "집계 대상 속성",
  { label: "작업", right: true },
] as const;

export function BillableMetricsTable({
  rows,
  onEdit,
  onDelete,
}: {
  rows: BillableMetricRowView[];
  onEdit: (row: BillableMetricRowView) => void;
  onDelete: (row: BillableMetricRowView) => void;
}) {
  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD} />
      {rows.map((row) => (
        <GridRow key={row.code} columns={COLUMNS} className="grid-row--actions">
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
          <GridCell className="grid-cell--actions">
            <button
              type="button"
              className="btn btn-ghost"
              style={{ fontSize: 13 }}
              aria-label={`${row.name} 수정`}
              onClick={() => onEdit(row)}
            >
              수정
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              style={{ fontSize: 13 }}
              aria-label={`${row.name} 삭제`}
              onClick={() => onDelete(row)}
            >
              삭제
            </button>
          </GridCell>
        </GridRow>
      ))}
    </GridTable>
  );
}
