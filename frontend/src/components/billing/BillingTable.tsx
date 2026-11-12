"use client";

import { Flex, Table, Tag, theme, Typography } from "antd";
import { useMemo } from "react";

import { useExpandedRowKeys } from "@/components/table/CollapseProvider";
import { CustomerLabel } from "@/components/table/CustomerLabel";
import { labeledExpandIcon } from "@/components/table/ExpandIcon";
import { countDimensionSlots, DimensionValues } from "@/components/table/DimensionValues";
import { MeterLabel } from "@/components/table/MeterLabel";
import { NumberText } from "@/components/table/NumberText";
import { koreanCompare, TABLE_STICKY } from "@/components/table/table-defaults";
import { useTableSort, type SortableColumn } from "@/components/table/table-sort";
import { ScopedLink } from "@/components/workspace/ScopedLink";
import type { DimensionValues as DimensionValueMap } from "@/lib/api/billable-metric-prices";
import type { ListCapabilities } from "@/lib/api/capabilities";
import { toMeterRows, type MeterRow } from "@/lib/grouped-rows";
import type { MonthlyListQuery } from "@/lib/list-query";
import { linesOf, type MeterItem } from "@/lib/meter-items";

export type BillingGroupView = {
  customerId: string;
  customerName: string;
  amount: string;
  amountValue: number;
  items: MeterItem<BillingLineView>[];
  unpricedUsage: BillingUnpricedUsageView | null;
  isHeld: boolean;
};

export type BillingLineView = {
  label: string;
  labelTooltip?: string;
  dimensionValues: DimensionValueMap;
  quantity: string;
  quantityBasis?: string;
  unitPrice: string;
  amount: string;
};

export type BillingUnpricedUsageView = {
  quantity: string;
  lines: BillingUnpricedLineView[];
};

export type BillingUnpricedLineView = {
  label: string;
  labelTooltip?: string;
  dimensionValues: DimensionValueMap;
  quantity: string;
};

type BillingUnpricedLineRow = {
  kind: "unpricedLine";
  key: string;
  line: BillingUnpricedLineView;
};

type BillingUnpricedRow = {
  kind: "unpriced";
  key: string;
  isCollapsedByDefault: true;
  combinationCount: number;
  quantity: string;
  children: BillingUnpricedLineRow[];
};

type BillingCustomerRow = {
  kind: "customer";
  key: string;
  customerId: string;
  customerName: string;
  amount: string;
  amountValue: number;
  lineCount: number;
  isHeld: boolean;
  isCollapsedByDefault: false;
  children?: BillingRow[];
};

type BillingRow =
  | BillingCustomerRow
  | MeterRow<BillingLineView>
  | BillingUnpricedRow
  | BillingUnpricedLineRow;

const MIN_WIDTH = 900;
const UNPRICED_LABEL = "기타 (단가 없음)";
const HELD_GUIDANCE = "이 달이 끝나기 전에 단가를 등록하세요. 단가 없는 사용량이 남으면 자동 확정이 보류되고, 월 전체 확정으로 단가 있는 사용량만 청구할 수 있어요.";
const PAST_HELD_GUIDANCE = "끝난 달에는 단가를 등록할 수 없어 자동 확정을 다시 시도하지 않아요. 월 전체 확정으로 단가 있는 사용량만 청구하세요.";

function byCustomerName(left: BillingRow, right: BillingRow): number {
  return left.kind === "customer" && right.kind === "customer"
    ? koreanCompare(left.customerName, right.customerName)
    : 0;
}

function byCustomerAmount(left: BillingRow, right: BillingRow): number {
  return left.kind === "customer" && right.kind === "customer"
    ? left.amountValue - right.amountValue
    : 0;
}

function toExpandLabel(row: BillingRow): string {
  if (row.kind === "customer") return row.customerName;
  if (row.kind === "meter") return row.label;
  if (row.kind === "unpriced") return UNPRICED_LABEL;
  return row.line.label;
}

const BILLING_EXPAND_ICON = labeledExpandIcon(toExpandLabel);

// 끝난 달은 단가를 등록할 수 없어(price_rate_history_immutable) 가격 화면 대신 수동 확정을 안내한다.
// 안내는 월 전체 확정 비활성 이유처럼 툴팁이 아닌 보이는 글자로 두고 표시가 그 글자를 설명으로 가리킨다.
function HeldTag({
  customerId,
  month,
  isPastMonth,
  guidanceId,
}: {
  customerId: string;
  month: string;
  isPastMonth: boolean;
  guidanceId: string;
}) {
  if (isPastMonth) {
    return (
      <Tag color="warning" variant="outlined" aria-describedby={guidanceId}>
        자동 확정 보류: 수동 확정 필요
      </Tag>
    );
  }
  const pricingHref = `/pricing?month=${month}&customer_id=${encodeURIComponent(customerId)}`;
  return (
    <Tag color="warning" variant="outlined">
      <ScopedLink href={pricingHref} aria-describedby={guidanceId} onClick={(event) => event.stopPropagation()}>
        자동 확정 보류: 단가 없는 사용량
      </ScopedLink>
    </Tag>
  );
}

function HeldGuidance({ id, isPastMonth }: { id: string; isPastMonth: boolean }) {
  return <p id={id} className="field-hint">{isPastMonth ? PAST_HELD_GUIDANCE : HELD_GUIDANCE}</p>;
}

function toUnpricedRow(customerId: string, unpricedUsage: BillingUnpricedUsageView): BillingUnpricedRow {
  const key = `${customerId}#unpriced`;
  return {
    kind: "unpriced",
    key,
    isCollapsedByDefault: true,
    combinationCount: unpricedUsage.lines.length,
    quantity: unpricedUsage.quantity,
    children: unpricedUsage.lines.map((line) => ({
      kind: "unpricedLine",
      key: `${key}/${line.label} ${JSON.stringify(line.dimensionValues)}`,
      line,
    })),
  };
}

function toCustomerRow(group: BillingGroupView): BillingCustomerRow {
  const children: BillingRow[] = [
    ...toMeterRows(group.customerId, group.items),
    ...(group.unpricedUsage ? [toUnpricedRow(group.customerId, group.unpricedUsage)] : []),
  ];
  return {
    kind: "customer",
    key: group.customerId,
    customerId: group.customerId,
    customerName: group.customerName,
    amount: group.amount,
    amountValue: group.amountValue,
    lineCount: linesOf(group.items).length,
    isHeld: group.isHeld,
    isCollapsedByDefault: false,
    ...(children.length > 0 ? { children } : {}),
  };
}

export function BillingTable({
  groups,
  totalAmount,
  query,
  capabilities,
  isPastMonth,
}: {
  groups: BillingGroupView[];
  totalAmount: string;
  query: MonthlyListQuery;
  capabilities: ListCapabilities;
  isPastMonth: boolean;
}) {
  const { token } = theme.useToken();
  const rows = useMemo(() => groups.map(toCustomerRow), [groups]);
  const slotCount = useMemo(
    () =>
      countDimensionSlots(
        groups.flatMap((group) => [
          ...linesOf(group.items).map((line) => line.dimensionValues),
          ...(group.unpricedUsage?.lines.map((line) => line.dimensionValues) ?? []),
        ]),
      ),
    [groups],
  );
  const { expandedRowKeys, onExpand, toggle } = useExpandedRowKeys<BillingRow>(rows);

  const sortableColumns: SortableColumn<BillingRow>[] = [
    {
      key: "label",
      title: "고객 / 미터 라인",
      ellipsis: true,
      sortKey: "customer_name",
      clientSorter: byCustomerName,
      onCell: (row) => ({ colSpan: row.kind === "customer" ? 2 : 1 }),
      render: (_, row) => {
        if (row.kind === "customer") {
          return row.isHeld ? (
            <>
              <Flex align="center" gap="small">
                <CustomerLabel name={row.customerName} id={row.customerId} />
                <HeldTag customerId={row.customerId} month={query.month} isPastMonth={isPastMonth} guidanceId={`held-guidance-${row.customerId}`} />
              </Flex>
              <HeldGuidance id={`held-guidance-${row.customerId}`} isPastMonth={isPastMonth} />
            </>
          ) : (
            <CustomerLabel name={row.customerName} id={row.customerId} />
          );
        }
        if (row.kind === "meter") {
          return <MeterLabel label={row.label} labelTooltip={row.labelTooltip} />;
        }
        if (row.kind === "unpriced") {
          return (
            <MeterLabel
              label={UNPRICED_LABEL}
              labelTooltip="단가가 등록되지 않은 조합이라 금액에 들지 않는 사용량"
              isCodeLabel={false}
            />
          );
        }
        if (row.kind === "line" && row.isNested) return null;
        return <MeterLabel label={row.line.label} labelTooltip={row.line.labelTooltip} />;
      },
    },
    {
      key: "combination",
      title: "조합",
      onCell: (row) => ({ colSpan: row.kind === "customer" ? 0 : 1 }),
      render: (_, row) => {
        if (row.kind === "line" || row.kind === "unpricedLine") {
          return <DimensionValues dimensionValues={row.line.dimensionValues} slotCount={slotCount} />;
        }
        if (row.kind === "meter" || row.kind === "unpriced") {
          return <Typography.Text type="secondary">조합 {row.combinationCount}개</Typography.Text>;
        }
        return null;
      },
    },
    {
      key: "quantity",
      title: "집계 수량",
      align: "right",
      width: 170,
      render: (_, row) => {
        if (row.kind === "customer") {
          return <Typography.Text type="secondary">청구 라인 {row.lineCount}줄</Typography.Text>;
        }
        if (row.kind === "meter" || row.kind === "unpriced") {
          return <NumberText>{row.quantity}</NumberText>;
        }
        return <div><NumberText>{row.line.quantity}</NumberText>{row.kind === "line" && row.line.quantityBasis && <p className="field-hint" style={{ overflowWrap: "anywhere" }}>{row.line.quantityBasis}</p>}</div>;
      },
    },
    {
      key: "unitPrice",
      title: "단가",
      align: "right",
      width: 130,
      render: (_, row) =>
        row.kind === "line" ? <NumberText type="secondary">{row.line.unitPrice}</NumberText> : null,
    },
    {
      key: "amount",
      title: "금액",
      align: "right",
      width: 180,
      sortKey: "amount",
      clientSorter: byCustomerAmount,
      render: (_, row) => {
        if (row.kind === "customer") return <NumberText strong>{row.amount}</NumberText>;
        if (row.kind === "meter") return <NumberText strong>{row.amount}</NumberText>;
        if (row.kind === "line") return <NumberText strong>{row.line.amount}</NumberText>;
        if (row.kind === "unpriced") return <Typography.Text type="secondary">청구 안 함</Typography.Text>;
        return null;
      },
    },
  ];
  const { columns, onChange } = useTableSort(query, capabilities, sortableColumns);

  return (
    <>
    <div className="data-mobile-records">{groups.map(group => <article key={group.customerId} className="data-mobile-record"><div className="data-mobile-line"><h3>{group.customerName}</h3><strong className="numeric">{group.amount}</strong></div><details><summary>청구 계산 내역</summary>{linesOf(group.items).map((line, index) => <div key={index}><div className="data-mobile-line"><span>{line.label}</span><strong className="numeric">{line.amount}</strong></div><p className="field-hint">수량 {line.quantity} / 단가 {line.unitPrice}</p>{line.quantityBasis && <p className="field-hint" style={{ overflowWrap: "anywhere" }}>{line.quantityBasis}</p>}{Object.keys(line.dimensionValues).length > 0 && <p className="field-hint">{Object.entries(line.dimensionValues).map(([key, value]) => `${key}: ${String(value)}`).join(", ")}</p>}</div>)}</details>{group.unpricedUsage && <p className="field-error">단가 없는 사용량 {group.unpricedUsage.quantity}, {group.unpricedUsage.lines.length}개 조합</p>}{group.isHeld && <><p><HeldTag customerId={group.customerId} month={query.month} isPastMonth={isPastMonth} guidanceId={`held-guidance-mobile-${group.customerId}`} /></p><HeldGuidance id={`held-guidance-mobile-${group.customerId}`} isPastMonth={isPastMonth} /></>}</article>)}<div className="data-mobile-total"><span>{query.customerId ? "선택 고객 공급가액" : "조건 전체 공급가액"}</span><span className="numeric">{totalAmount}</span></div></div>
    <div className="data-desktop-table"><Table<BillingRow>
      rowKey="key"
      columns={columns}
      dataSource={rows}
      onChange={onChange}
      pagination={false}
      size="medium"
      sticky={TABLE_STICKY}
      tableLayout="fixed"
      scroll={{ x: MIN_WIDTH }}
      expandable={{ expandedRowKeys, onExpand, indentSize: 16, expandIcon: BILLING_EXPAND_ICON }}
      onRow={(row) =>
        (row.kind === "customer" || row.kind === "meter" || row.kind === "unpriced") &&
        (row.children?.length ?? 0) > 0
          ? { onClick: () => toggle(row.key), style: { cursor: "pointer" } }
          : {}
      }
      summary={() => (
        <Table.Summary>
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>
              <Typography.Text strong>{query.customerId ? "선택 고객 공급가액" : "조건 전체 공급가액"}</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={1} colSpan={3} />
            <Table.Summary.Cell index={4} align="right">
              <Typography.Text
                strong
                style={{
                  fontSize: token.fontSizeLG,
                  fontVariantNumeric: "tabular-nums",
                  whiteSpace: "nowrap",
                }}
              >
                {totalAmount}
              </Typography.Text>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        </Table.Summary>
      )}
    /></div>
    </>
  );
}
