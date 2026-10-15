"use client";

import { useCollapse } from "@/components/table/CollapseProvider";
import { countDimensionSlots, DimensionValuesCell } from "@/components/table/DimensionValuesCell";
import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRow } from "@/components/table/GridRow";
import { GridTable } from "@/components/table/GridTable";
import { MeterGroupRow } from "@/components/table/MeterGroupRow";
import { MeterLabel } from "@/components/table/MeterLabel";
import type { DimensionValues } from "@/lib/api/billable-metric-prices";
import { linesOf, type MeterItem } from "@/lib/meter-items";

export type BillingGroupView = {
  customerId: string;
  customerName: string;
  amount: string;
  items: MeterItem<BillingLineView>[];
};

export type BillingLineView = {
  label: string;
  labelTooltip?: string;
  dimensionValues: DimensionValues;
  quantity: string;
  unitPrice: string;
  amount: string;
};

const COLUMNS = "minmax(0, 1fr) minmax(0, 1fr) 170px 130px 180px";
const MIN_WIDTH = 900;

const HEAD_LABELS = [
  "고객 / 미터 라인",
  "조합",
  { label: "집계 수량", right: true },
  { label: "단가", right: true },
  { label: "금액", right: true },
] as const;

export function BillingTable({
  groups,
  totalAmount,
}: {
  groups: BillingGroupView[];
  totalAmount: string;
}) {
  const { isCollapsed, toggle } = useCollapse();
  const slotCount = countDimensionSlots(
    groups.flatMap((group) => linesOf(group.items).map((line) => line.dimensionValues)),
  );

  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD_LABELS} />
      {groups.map((group) => {
        const isGroupCollapsed = isCollapsed(group.customerId);
        return (
          <div key={group.customerId}>
            <button
              type="button"
              className="grid-row grid-row--clickable grid-row--group"
              style={{ gridTemplateColumns: COLUMNS }}
              aria-expanded={!isGroupCollapsed}
              onClick={() => toggle(group.customerId)}
            >
              <div className="grid-cell grid-cell--group grid-cell--span-2 grid-cell--truncate">
                <span aria-hidden>{isGroupCollapsed ? "▸" : "▾"}</span>{" "}
                {group.customerName}{" "}
                <span className="grid-cell__id">{group.customerId}</span>
              </div>
              <div className="grid-cell grid-cell--group grid-cell--right grid-cell__count">
                청구 라인 {linesOf(group.items).length}줄
              </div>
              <div className="grid-cell grid-cell--group" />
              <div className="grid-cell grid-cell--group grid-cell--right grid-cell--num">
                {group.amount}
              </div>
            </button>

            {isGroupCollapsed
              ? null
              : group.items.map((item) => {
                  if (item.kind === "line") {
                    return (
                      <BillingLineRow
                        key={`${item.line.label} ${JSON.stringify(item.line.dimensionValues)}`}
                        line={item.line}
                        slotCount={slotCount}
                      />
                    );
                  }
                  const isMeterCollapsed = isCollapsed(item.id, true);
                  return (
                    <div key={item.id}>
                      <MeterGroupRow
                        columns={COLUMNS}
                        label={item.label}
                        labelTooltip={item.labelTooltip}
                        combinationCount={item.lines.length}
                        isCollapsed={isMeterCollapsed}
                        onToggle={() => toggle(item.id)}
                      >
                        <div className="grid-cell grid-cell--right grid-cell--num">
                          {item.quantity}
                        </div>
                        <div className="grid-cell" />
                        <div className="grid-cell grid-cell--right grid-cell--num grid-cell--strong">
                          {item.amount}
                        </div>
                      </MeterGroupRow>
                      {isMeterCollapsed
                        ? null
                        : item.lines.map((line) => (
                            <BillingLineRow
                              key={JSON.stringify(line.dimensionValues)}
                              line={line}
                              slotCount={slotCount}
                              isNested
                            />
                          ))}
                    </div>
                  );
                })}
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

function BillingLineRow({
  line,
  slotCount,
  isNested = false,
}: {
  line: BillingLineView;
  slotCount: number;
  isNested?: boolean;
}) {
  return (
    <GridRow columns={COLUMNS}>
      <GridCell className="grid-cell--child grid-cell--mono">
        {isNested ? null : (
          <MeterLabel label={line.label} labelTooltip={line.labelTooltip} />
        )}
      </GridCell>
      <DimensionValuesCell
        dimensionValues={line.dimensionValues}
        slotCount={slotCount}
        className={isNested ? "grid-cell--nested" : undefined}
      />
      <GridCell className="grid-cell--right grid-cell--num">{line.quantity}</GridCell>
      <GridCell className="grid-cell--right grid-cell--num grid-cell--muted">
        {line.unitPrice}
      </GridCell>
      <GridCell className="grid-cell--right grid-cell--num grid-cell--strong">
        {line.amount}
      </GridCell>
    </GridRow>
  );
}
