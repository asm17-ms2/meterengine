import type { BillableMetricPriceRowView } from "@/app/(console)/billable-metric-prices/state";
import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRow } from "@/components/table/GridRow";
import { GridTable } from "@/components/table/GridTable";

const COLUMNS = "190px minmax(0, 1fr) 240px 150px 190px";
const MIN_WIDTH = 940;

const HEAD_LABELS = [
  "코드",
  "이름",
  "단가를 가르는 속성",
  { label: "기본 단가", right: true },
  { label: "상태", right: true },
] as const;

function DimensionPropertiesCell({ dimensionProperties }: { dimensionProperties: string[] | null }) {
  if (dimensionProperties === null) {
    return <GridCell className="grid-cell--muted">정책 없음</GridCell>;
  }
  if (dimensionProperties.length === 0) {
    return <GridCell className="grid-cell--muted">속성 구분 없음</GridCell>;
  }
  return (
    <GridCell>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
        {dimensionProperties.map((dimensionProperty) => (
          <span key={dimensionProperty} className="tag tag-neutral grid-cell--mono">
            {dimensionProperty}
          </span>
        ))}
      </div>
    </GridCell>
  );
}

function StatusCell({ row }: { row: BillableMetricPriceRowView }) {
  if (row.status === "no_policy") {
    return <GridCell className="grid-cell--right grid-cell--muted">정책 없음</GridCell>;
  }
  if (row.status === "no_rate") {
    return (
      <GridCell className="grid-cell--right">
        <span className="tag tag-accent">단가 없음, 청구 제외</span>
      </GridCell>
    );
  }
  return <GridCell className="grid-cell--right grid-cell--muted">청구 중</GridCell>;
}

export function BillableMetricPricesTable({ rows }: { rows: BillableMetricPriceRowView[] }) {
  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD_LABELS} />
      {rows.map((row) => (
        <GridRow key={row.code} columns={COLUMNS}>
          <GridCell className="grid-cell--mono grid-cell--strong grid-cell--truncate">
            {row.code}
          </GridCell>
          <GridCell className="grid-cell--truncate">{row.name}</GridCell>
          <DimensionPropertiesCell dimensionProperties={row.dimensionProperties} />
          <GridCell
            className={
              row.unitPrice === null ? "grid-cell--num grid-cell--muted" : "grid-cell--num"
            }
          >
            {row.unitPrice ?? "없음"}
          </GridCell>
          <StatusCell row={row} />
        </GridRow>
      ))}
    </GridTable>
  );
}
