"use client";

import { useCollapse } from "@/components/table/CollapseProvider";
import { countDimensionSlots, DimensionValuesCell } from "@/components/table/DimensionValuesCell";
import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRow } from "@/components/table/GridRow";
import { GridTable } from "@/components/table/GridTable";
import { MeterGroupRow } from "@/components/table/MeterGroupRow";
import { MeterLabel } from "@/components/table/MeterLabel";
import { linesOf, type MeterItem } from "@/lib/meter-items";

export type UsageGroupView = {
  customerId: string;
  customerName: string;
  items: MeterItem<UsageLineView>[];
};

export type UsageLineView = {
  label: string;
  labelTooltip?: string;
  dimensionValues: Record<string, unknown>;
  quantity: string;
};

const COLUMNS = "minmax(0, 1fr) minmax(0, 1fr) 200px";
const MIN_WIDTH = 760;

const HEAD_LABELS = ["고객 / 미터", "조합", { label: "집계 수량", right: true }] as const;

export function UsageTable({ groups }: { groups: UsageGroupView[] }) {
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
                집계 라인 {linesOf(group.items).length}줄
              </div>
            </button>

            {isGroupCollapsed
              ? null
              : group.items.map((item) => {
                  if (item.kind === "line") {
                    return (
                      <UsageLineRow
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
                        <div className="grid-cell grid-cell--right grid-cell--num grid-cell--strong">
                          {item.quantity}
                        </div>
                      </MeterGroupRow>
                      {isMeterCollapsed
                        ? null
                        : item.lines.map((line) => (
                            <UsageLineRow
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
    </GridTable>
  );
}

function UsageLineRow({
  line,
  slotCount,
  isNested = false,
}: {
  line: UsageLineView;
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
      <GridCell className="grid-cell--right grid-cell--num grid-cell--strong">
        {line.quantity}
      </GridCell>
    </GridRow>
  );
}
