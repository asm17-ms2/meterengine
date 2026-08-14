"use client";

import { useCollapse } from "@/components/table/CollapseProvider";
import { GridCell, GridHead, GridRow, GridTable } from "@/components/table/Grid";

/**
 * 화면이 그리는 뷰모델. 숫자 포맷은 서버에서 끝내고 문자열로 받는다.
 * 클라이언트에서 toLocaleString을 부르면 서버와 결과가 달라져 하이드레이션이 어긋난다.
 */
export type UsageGroupView = {
  customerId: string;
  customerName: string;
  /** UUID 마지막 그룹. 이름 옆에 붙는 짧은 식별자다. */
  customerIdTail: string;
  meters: { label: string; quantity: string }[];
};

const COLUMNS = "minmax(0, 1fr) 200px";
const MIN_WIDTH = 640;

const HEAD = ["고객 / 미터", { label: "집계 수량", right: true }] as const;

export function UsageTable({ groups }: { groups: UsageGroupView[] }) {
  const { isCollapsed, toggle } = useCollapse();

  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD} />
      {groups.map((group) => {
        const collapsed = isCollapsed(group.customerId);
        return (
          <div key={group.customerId}>
            <button
              type="button"
              className="grid-row grid-row--clickable grid-row--group"
              style={{ gridTemplateColumns: COLUMNS }}
              aria-expanded={!collapsed}
              onClick={() => toggle(group.customerId)}
            >
              <div className="grid-cell grid-cell--group">
                <span aria-hidden>{collapsed ? "▸" : "▾"}</span>{" "}
                {group.customerName}{" "}
                <span className="grid-cell__id">{group.customerIdTail}</span>
              </div>
              <div className="grid-cell grid-cell--group grid-cell--right grid-cell__count">
                미터 {group.meters.length}개
              </div>
            </button>

            {collapsed
              ? null
              : group.meters.map((meter) => (
                  <GridRow key={meter.label} columns={COLUMNS}>
                    <GridCell className="grid-cell--child grid-cell--mono">
                      {meter.label}
                    </GridCell>
                    <GridCell className="grid-cell--right grid-cell--num grid-cell--strong">
                      {meter.quantity}
                    </GridCell>
                  </GridRow>
                ))}
          </div>
        );
      })}
    </GridTable>
  );
}
