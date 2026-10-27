"use client";

import { Table, Typography } from "antd";
import { useMemo } from "react";

import { useExpandedRowKeys } from "@/components/table/CollapseProvider";
import { CustomerLabel } from "@/components/table/CustomerLabel";
import { labeledExpandIcon } from "@/components/table/ExpandIcon";
import { countDimensionSlots, DimensionValues } from "@/components/table/DimensionValues";
import { MeterLabel } from "@/components/table/MeterLabel";
import { NumberText } from "@/components/table/NumberText";
import { koreanCompare, TABLE_STICKY } from "@/components/table/table-defaults";
import { useTableSort, type SortableColumn } from "@/components/table/table-sort";
import type { ListCapabilities } from "@/lib/api/capabilities";
import { toMeterRows, type MeterRow } from "@/lib/grouped-rows";
import type { ListQuery } from "@/lib/list-query";
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
  rawQuantity?: string;
  overallRawQuantity?: string;
};

type CustomerRow = {
  kind: "customer";
  key: string;
  customerId: string;
  customerName: string;
  lineCount: number;
  isCollapsedByDefault: false;
  children?: MeterRow<UsageLineView>[];
};

type UsageRow = CustomerRow | MeterRow<UsageLineView>;

const TABLE_SCROLL = { x: 760 };

function byCustomerName(left: UsageRow, right: UsageRow): number {
  return left.kind === "customer" && right.kind === "customer"
    ? koreanCompare(left.customerName, right.customerName)
    : 0;
}

function byLineCount(left: UsageRow, right: UsageRow): number {
  return left.kind === "customer" && right.kind === "customer" ? left.lineCount - right.lineCount : 0;
}

function toExpandLabel(row: UsageRow): string {
  if (row.kind === "customer") return row.customerName;
  if (row.kind === "meter") return row.label;
  return row.line.label;
}

const USAGE_EXPAND_ICON = labeledExpandIcon(toExpandLabel);

function hasChildRows(row: UsageRow): boolean {
  return row.kind !== "line" && (row.children?.length ?? 0) > 0;
}

export function UsageTable({
  groups,
  query,
  capabilities,
}: {
  groups: UsageGroupView[];
  query: ListQuery;
  capabilities: ListCapabilities;
}) {

  const rows = useMemo<UsageRow[]>(
    () =>
      groups.map((group) => {
        const children = toMeterRows(group.customerId, group.items);
        return {
          kind: "customer",
          key: group.customerId,
          customerId: group.customerId,
          customerName: group.customerName,
          lineCount: linesOf(group.items).length,
          isCollapsedByDefault: false,
          ...(children.length > 0 ? { children } : {}),
        };
      }),
    [groups],
  );

  const slotCount = useMemo(
    () =>
      countDimensionSlots(
        groups.flatMap((group) => linesOf(group.items).map((line) => line.dimensionValues)),
      ),
    [groups],
  );

  const { expandedRowKeys, onExpand, toggle } = useExpandedRowKeys(rows);

  const sortableColumns: SortableColumn<UsageRow>[] = [
    {
      key: "label",
      title: "고객 / 미터",
      ellipsis: true,
      clientSorter: byCustomerName,
      onCell: (row) => ({ colSpan: row.kind === "customer" ? 2 : 1 }),
      render: (_, row) => {
        if (row.kind === "customer") {
          return (
            <CustomerLabel name={row.customerName} id={row.customerId} />
          );
        }
        if (row.kind === "meter") {
          return <MeterLabel label={row.label} labelTooltip={row.labelTooltip} />;
        }
        return row.isNested ? null : (
          <MeterLabel label={row.line.label} labelTooltip={row.line.labelTooltip} />
        );
      },
    },
    {
      key: "combination",
      title: "조합",
      onCell: (row) => ({ colSpan: row.kind === "customer" ? 0 : 1 }),
      render: (_, row) => {
        if (row.kind === "line") {
          return <DimensionValues dimensionValues={row.line.dimensionValues} slotCount={slotCount} />;
        }
        if (row.kind === "meter") {
          return <Typography.Text type="secondary">전체 집계값 / 조합 {row.combinationCount}개</Typography.Text>;
        }
        return null;
      },
    },
    {
      key: "quantity",
      title: "집계 수량",
      align: "right",
      width: 200,
      clientSorter: byLineCount,
      render: (_, row) => {
        if (row.kind === "customer") {
          return <Typography.Text type="secondary">집계 라인 {row.lineCount}줄</Typography.Text>;
        }
        const rawQuantity = row.kind === "meter" ? row.children[0]?.line.overallRawQuantity : row.line.rawQuantity;
        return <div><NumberText strong>{row.kind === "meter" ? row.quantity : row.line.quantity}</NumberText>{rawQuantity != null ? <p className="field-hint">단위 / 반올림 적용 전 {rawQuantity}</p> : null}</div>;
      },
    },
  ];

  const { columns, onChange } = useTableSort(query, capabilities, sortableColumns);

  return (
    <>
    <div className="data-mobile-records">{groups.map(group => <article key={group.customerId} className="data-mobile-record"><h3>{group.customerName}</h3>{group.items.map((item, index) => <div key={index}>{item.kind === "meter" && <><div className="data-mobile-line"><span>{item.label} / 전체 집계값</span><strong className="numeric">{item.quantity}</strong></div>{item.lines[0]?.overallRawQuantity != null && <p className="field-hint">단위 / 반올림 적용 전 {item.lines[0].overallRawQuantity}</p>}</>}{linesOf([item]).map((line, lineIndex) => <div key={lineIndex}><div className="data-mobile-line"><span>{item.kind === "meter" ? "차원별 집계값" : line.label}</span><strong className="numeric">{line.quantity}</strong></div>{Object.keys(line.dimensionValues).length > 0 && <p className="field-hint">{Object.entries(line.dimensionValues).map(([key, value]) => `${key}: ${String(value)}`).join(", ")}</p>}{line.rawQuantity != null && <p className="field-hint">단위 / 반올림 적용 전 {line.rawQuantity}</p>}</div>)}</div>)}</article>)}</div>
    <div className="data-desktop-table"><Table<UsageRow>
      rowKey="key"
      columns={columns}
      dataSource={rows}
      onChange={onChange}
      pagination={false}
      size="medium"
      sticky={TABLE_STICKY}
      tableLayout="fixed"
      scroll={TABLE_SCROLL}
      expandable={{ expandedRowKeys, onExpand, indentSize: 16, expandIcon: USAGE_EXPAND_ICON }}
      onRow={(row) =>
        hasChildRows(row) ? { onClick: () => toggle(row.key), style: { cursor: "pointer" } } : {}
      }
    /></div>
    </>
  );
}
