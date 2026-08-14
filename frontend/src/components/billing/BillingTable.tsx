"use client";

import { useCollapse } from "@/components/table/CollapseProvider";
import { GridCell, GridHead, GridRow, GridTable } from "@/components/table/Grid";

/**
 * 화면이 그리는 뷰모델. 숫자 포맷은 서버에서 끝내고 문자열로 받는다.
 * 클라이언트에서 toLocaleString을 부르면 서버와 결과가 달라져 하이드레이션이 어긋난다.
 */
export type BillingGroupView = {
  customerId: string;
  customerName: string;
  /** UUID 마지막 그룹. 이름 옆에 붙는 짧은 식별자다. */
  customerIdTail: string;
  /** 고객 소계. 서버가 준 amount를 원화 표기만 한 것이다. */
  amount: string;
  lines: BillingLineView[];
};

export type BillingLineView = {
  label: string;
  quantity: string;
  unitPrice: string;
  amount: string;
};

const COLUMNS = "minmax(0, 1fr) 170px 130px 180px";
const MIN_WIDTH = 760;

const HEAD = [
  "고객 / 미터 라인",
  { label: "집계 수량", right: true },
  { label: "단가", right: true },
  { label: "금액", right: true },
] as const;

export function BillingTable({
  groups,
  totalAmount,
}: {
  groups: BillingGroupView[];
  /** 전체 합계. 서버가 준 total_amount 그대로다. */
  totalAmount: string;
}) {
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
                청구 라인 {group.lines.length}줄
              </div>
              <div className="grid-cell grid-cell--group" />
              <div className="grid-cell grid-cell--group grid-cell--right grid-cell--num">
                {group.amount}
              </div>
            </button>

            {collapsed
              ? null
              : group.lines.map((line) => (
                  <GridRow key={line.label} columns={COLUMNS}>
                    <GridCell className="grid-cell--child grid-cell--mono">
                      {line.label}
                    </GridCell>
                    <GridCell className="grid-cell--right grid-cell--num">
                      {line.quantity}
                    </GridCell>
                    <GridCell className="grid-cell--right grid-cell--num grid-cell--muted">
                      {line.unitPrice}
                    </GridCell>
                    <GridCell className="grid-cell--right grid-cell--num grid-cell--strong">
                      {line.amount}
                    </GridCell>
                  </GridRow>
                ))}
          </div>
        );
      })}

      <GridRow columns={COLUMNS} className="grid-row--total">
        <GridCell className="grid-cell--group">합계</GridCell>
        <GridCell />
        <GridCell />
        <GridCell className="grid-cell--right grid-cell--num grid-cell__total-amount">
          {totalAmount}
        </GridCell>
      </GridRow>
    </GridTable>
  );
}
