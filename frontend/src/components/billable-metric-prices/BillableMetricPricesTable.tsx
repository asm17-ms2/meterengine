import type { BillableMetricPriceRowView } from "@/app/(console)/billable-metric-prices/state";
import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRow } from "@/components/table/GridRow";
import { GridTable } from "@/components/table/GridTable";

const COLUMNS = "190px minmax(0, 1fr) 220px 140px 110px 110px";
const MIN_WIDTH = 960;

const HEAD_LABELS = [
  "코드",
  "이름",
  "단가를 가르는 속성",
  { label: "기본 단가", right: true },
  "상태",
  { label: "작업", right: true },
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

function StatusCell({ status }: { status: BillableMetricPriceRowView["status"] }) {
  if (status === "no_policy") return <GridCell>정책 없음</GridCell>;
  if (status === "no_rate") {
    return (
      <GridCell className="grid-cell--strong">
        <span style={{ color: "var(--color-accent-700)" }}>단가 없음</span>
      </GridCell>
    );
  }
  return <GridCell className="grid-cell--muted">청구 중</GridCell>;
}

function ActionCell({
  row,
  onRegisterPolicy,
}: {
  row: BillableMetricPriceRowView;
  onRegisterPolicy: (row: BillableMetricPriceRowView) => void;
}) {
  if (row.status !== "no_policy") return <GridCell className="grid-cell--actions" />;
  return (
    <GridCell className="grid-cell--actions">
      <button
        type="button"
        className="btn btn-ghost"
        style={{ fontSize: 13 }}
        aria-label={`${row.name} 가격 정책 등록`}
        onClick={() => onRegisterPolicy(row)}
      >
        정책 등록
      </button>
    </GridCell>
  );
}

export function BillableMetricPricesTable({
  rows,
  onRegisterPolicy,
}: {
  rows: BillableMetricPriceRowView[];
  onRegisterPolicy: (row: BillableMetricPriceRowView) => void;
}) {
  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD_LABELS} />
      {rows.map((row) => (
        <GridRow key={row.code} columns={COLUMNS} className="grid-row--actions">
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
          <StatusCell status={row.status} />
          <ActionCell row={row} onRegisterPolicy={onRegisterPolicy} />
        </GridRow>
      ))}
    </GridTable>
  );
}
