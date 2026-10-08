import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRow } from "@/components/table/GridRow";
import { GridTable } from "@/components/table/GridTable";

export type InvoiceRowView = {
  id: string;
  customerId: string;
  customerName: string;
  /** `2026년 9월`. */
  month: string;
  /** `2026-10-01 14:00:07` (KST). */
  finalizedAt: string;
  /** `2,184,920원`. */
  totalAmount: string;
};

export type InvoiceTotalRowView = {
  customerCount: number;
  totalAmount: string;
};

const COLUMNS = "minmax(0, 1fr) 130px 190px 160px";
const MIN_WIDTH = 760;

const HEAD_LABELS = [
  "고객",
  "대상 기간",
  "확정 시각",
  { label: "합계 금액", right: true },
] as const;

export function InvoicesTable({
  rows,
  totalRow,
}: {
  rows: InvoiceRowView[];
  /** 달을 고른 때만 있다. 전체 기간의 합은 청구 금액으로 오해될 수 있어 그리지 않는다. */
  totalRow: InvoiceTotalRowView | null;
}) {
  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD_LABELS} />
      {rows.map((row) => (
        <GridRow key={row.id} columns={COLUMNS}>
          <GridCell className="grid-cell--strong grid-cell--truncate">
            {row.customerName} <span className="grid-cell__id">{row.customerId}</span>
          </GridCell>
          <GridCell>{row.month}</GridCell>
          <GridCell className="grid-cell--num">{row.finalizedAt}</GridCell>
          <GridCell className="grid-cell--right grid-cell--num grid-cell--strong">
            {row.totalAmount}
          </GridCell>
        </GridRow>
      ))}

      {totalRow ? (
        <GridRow columns={COLUMNS} className="grid-row--total">
          <GridCell className="grid-cell--group">합계</GridCell>
          <GridCell className="grid-cell--group grid-cell__count">
            고객 {totalRow.customerCount}곳
          </GridCell>
          <GridCell />
          <GridCell className="grid-cell--right grid-cell--num grid-cell__total-amount">
            {totalRow.totalAmount}
          </GridCell>
        </GridRow>
      ) : null}
    </GridTable>
  );
}
