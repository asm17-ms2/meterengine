"use client";

import { useCollapse } from "@/components/table/CollapseProvider";
import { countDimensionSlots, DimensionValuesCell } from "@/components/table/DimensionValuesCell";
import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRow } from "@/components/table/GridRow";
import { GridTable } from "@/components/table/GridTable";
import type { DimensionValues } from "@/lib/api/billable-metric-prices";

export type InvoiceGroupView = {
  id: string;
  customerId: string;
  customerName: string;
  finalizedAt: string;
  supplyAmount: string;
  taxAmount: string;
  totalAmount: string;
  lines: InvoiceLineView[];
};

export type InvoiceLineView = {
  label: string;
  dimensionValues: DimensionValues;
  quantity: string;
  unitPrice: string;
  amount: string;
};

const COLUMNS = "minmax(0, 1fr) minmax(0, 1fr) 190px 130px 180px";
const MIN_WIDTH = 920;

const HEAD_LABELS = [
  "고객 / 미터 라인",
  "조합",
  { label: "확정 시각 / 수량", right: true },
  { label: "단가", right: true },
  { label: "금액", right: true },
] as const;

export function InvoicesTable({
  groups,
  totalAmount,
}: {
  groups: InvoiceGroupView[];
  totalAmount: string;
}) {
  const { isCollapsed, toggle } = useCollapse();
  const slotCount = countDimensionSlots(
    groups.flatMap((group) => group.lines.map((line) => line.dimensionValues)),
  );

  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD_LABELS} />
      {groups.map((group) => {
        const isGroupCollapsed = isCollapsed(group.id);
        return (
          <div key={group.id}>
            <button
              type="button"
              className="grid-row grid-row--clickable grid-row--group"
              style={{ gridTemplateColumns: COLUMNS }}
              aria-expanded={!isGroupCollapsed}
              onClick={() => toggle(group.id)}
            >
              <div className="grid-cell grid-cell--group grid-cell--span-2 grid-cell--truncate">
                <span aria-hidden>{isGroupCollapsed ? "▸" : "▾"}</span> {group.customerName}{" "}
                <span className="grid-cell__id">{group.customerId}</span>
              </div>
              <div className="grid-cell grid-cell--group grid-cell--right grid-cell--num">
                {group.finalizedAt}
              </div>
              <div className="grid-cell grid-cell--group" />
              <div className="grid-cell grid-cell--group grid-cell--right grid-cell--num">
                {group.totalAmount}
              </div>
            </button>

            {isGroupCollapsed ? null : (
              <>
                {group.lines.map((line) => (
                  <GridRow
                    key={`${line.label} ${JSON.stringify(line.dimensionValues)}`}
                    columns={COLUMNS}
                  >
                    <GridCell className="grid-cell--child grid-cell--mono">{line.label}</GridCell>
                    <DimensionValuesCell
                      dimensionValues={line.dimensionValues}
                      slotCount={slotCount}
                    />
                    <GridCell className="grid-cell--right grid-cell--num">{line.quantity}</GridCell>
                    <GridCell className="grid-cell--right grid-cell--num grid-cell--muted">
                      {line.unitPrice}
                    </GridCell>
                    <GridCell className="grid-cell--right grid-cell--num grid-cell--strong">
                      {line.amount}
                    </GridCell>
                  </GridRow>
                ))}
                <AmountRow label="공급가액" amount={group.supplyAmount} />
                <AmountRow label="세액" amount={group.taxAmount} />
              </>
            )}
          </div>
        );
      })}

      <GridRow columns={COLUMNS} className="grid-row--total">
        <GridCell className="grid-cell--group">합계</GridCell>
        <GridCell />
        <GridCell />
        <GridCell />
        <GridCell className="grid-cell--right grid-cell--num grid-cell__total-amount">
          {totalAmount}
        </GridCell>
      </GridRow>
    </GridTable>
  );
}

function AmountRow({ label, amount }: { label: string; amount: string }) {
  return (
    <GridRow columns={COLUMNS}>
      <GridCell className="grid-cell--child grid-cell--muted">{label}</GridCell>
      <GridCell />
      <GridCell />
      <GridCell />
      <GridCell className="grid-cell--right grid-cell--num grid-cell--muted">{amount}</GridCell>
    </GridRow>
  );
}
