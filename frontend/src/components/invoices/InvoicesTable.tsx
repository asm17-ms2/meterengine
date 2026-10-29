"use client";

import { Flex, Table, Tag, theme, Typography } from "antd";
import { useMemo } from "react";
import Link from "next/link";

import { useExpandedRowKeys } from "@/components/table/CollapseProvider";
import { CustomerLabel } from "@/components/table/CustomerLabel";
import { labeledExpandIcon } from "@/components/table/ExpandIcon";
import { countDimensionSlots, DimensionValues } from "@/components/table/DimensionValues";
import { MeterLabel } from "@/components/table/MeterLabel";
import { NumberText } from "@/components/table/NumberText";
import { koreanCompare, TABLE_STICKY, timestampCompare } from "@/components/table/table-defaults";
import { type SortableColumn, useTableSort } from "@/components/table/table-sort";
import type { DimensionValues as DimensionValueMap } from "@/lib/api/billable-metric-prices";
import type { ListCapabilities } from "@/lib/api/capabilities";
import { type MeterRow, toMeterRows } from "@/lib/grouped-rows";
import type { ListQuery } from "@/lib/list-query";
import { linesOf, type MeterItem } from "@/lib/meter-items";

export type InvoiceGroupView = {
  id: string;
  invoiceNumber?: string;
  customerId: string;
  customerName: string;
  month: string;
  finalizedAt: string;
  finalizedAtIso: string;
  supplyAmount: string;
  taxAmount: string;
  totalAmount: string;
  totalAmountValue: number;
  items: MeterItem<InvoiceLineView>[];
};

export type InvoiceLineView = {
  label: string;
  labelTooltip?: string;
  dimensionValues: DimensionValueMap;
  quantity: string;
  quantityBasis?: string;
  unitPrice: string;
  amount: string;
};

type InvoiceAmountRow = { kind: "amount"; key: string; label: string; amount: string };

type InvoiceChildRow = MeterRow<InvoiceLineView> | InvoiceAmountRow;

type InvoiceSummaryRow = {
  kind: "invoice";
  key: string;
  invoiceNumber?: string;
  isCollapsedByDefault: false;
  customerId: string;
  customerName: string;
  month: string;
  finalizedAt: string;
  finalizedAtIso: string;
  totalAmount: string;
  totalAmountValue: number;
  children: InvoiceChildRow[];
};

type InvoiceRow = InvoiceSummaryRow | InvoiceChildRow;

const TABLE_MIN_WIDTH = 920;

function compareInvoices(
  left: InvoiceRow,
  right: InvoiceRow,
  compare: (left: InvoiceSummaryRow, right: InvoiceSummaryRow) => number,
): number {
  return left.kind === "invoice" && right.kind === "invoice" ? compare(left, right) : 0;
}

function toExpandLabel(row: InvoiceRow): string {
  if (row.kind === "invoice") return `${row.customerName} ${row.month}`;
  if (row.kind === "meter" || row.kind === "amount") return row.label;
  return row.line.label;
}

const INVOICE_EXPAND_ICON = labeledExpandIcon(toExpandLabel);

function toInvoiceRow(group: InvoiceGroupView): InvoiceSummaryRow {
  return {
    kind: "invoice",
    key: group.id,
    invoiceNumber: group.invoiceNumber,
    isCollapsedByDefault: false,
    customerId: group.customerId,
    customerName: group.customerName,
    month: group.month,
    finalizedAt: group.finalizedAt,
    finalizedAtIso: group.finalizedAtIso,
    totalAmount: group.totalAmount,
    totalAmountValue: group.totalAmountValue,
    children: [
      ...toMeterRows(group.id, group.items),
      { kind: "amount", key: `${group.id}#supply`, label: "공급가액", amount: group.supplyAmount },
      { kind: "amount", key: `${group.id}#tax`, label: "세액", amount: group.taxAmount },
    ],
  };
}

export function InvoicesTable({
  groups,
  totalAmount,
  isAllMonths,
  query,
  capabilities,
}: {
  groups: InvoiceGroupView[];
  totalAmount: string;
  isAllMonths: boolean;
  query: ListQuery;
  capabilities: ListCapabilities;
}) {
  const { token } = theme.useToken();
  const rows = useMemo<InvoiceRow[]>(() => groups.map(toInvoiceRow), [groups]);
  const slotCount = useMemo(
    () =>
      countDimensionSlots(
        groups.flatMap((group) => linesOf(group.items).map((line) => line.dimensionValues)),
      ),
    [groups],
  );
  const { expandedRowKeys, onExpand, toggle } = useExpandedRowKeys(rows);

  const sortableColumns: SortableColumn<InvoiceRow>[] = [
    {
      key: "label",
      title: "고객 / 미터 라인",
      sortKey: "customer_name",
      clientSorter: (left, right) =>
        compareInvoices(left, right, (a, b) => koreanCompare(a.customerName, b.customerName)),
      onCell: (row) => ({ colSpan: row.kind === "invoice" ? 2 : 1 }),
      render: (_, row) => {
        if (row.kind === "invoice") {
          return (
            <div style={{ display: "grid", gap: 8, minWidth: 0 }}>
            <Flex align="center" gap="small" style={{ minWidth: 0 }}>
              <CustomerLabel name={row.customerName} id={row.customerId} />
              {isAllMonths ? <Tag variant="outlined">{row.month}</Tag> : null}
            </Flex>
            <div style={{ display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
              {row.invoiceNumber && <Typography.Text type="secondary">{row.invoiceNumber}</Typography.Text>}
              <Link href={`/payments/${encodeURIComponent(row.key)}`} onClick={event => event.stopPropagation()}>수납 상세</Link>
            </div>
            </div>
          );
        }
        if (row.kind === "meter") {
          return <MeterLabel label={row.label} labelTooltip={row.labelTooltip} />;
        }
        if (row.kind === "amount") {
          return <Typography.Text type="secondary">{row.label}</Typography.Text>;
        }
        return row.isNested ? null : (
          <MeterLabel label={row.line.label} labelTooltip={row.line.labelTooltip} />
        );
      },
    },
    {
      key: "combination",
      title: "조합",
      onCell: (row) => ({ colSpan: row.kind === "invoice" ? 0 : 1 }),
      render: (_, row) => {
        if (row.kind === "line") {
          return <DimensionValues dimensionValues={row.line.dimensionValues} slotCount={slotCount} />;
        }
        if (row.kind === "meter") {
          return <Typography.Text type="secondary">조합 {row.combinationCount}개</Typography.Text>;
        }
        return null;
      },
    },
    {
      key: "quantity",
      title: "확정 시각 / 수량",
      align: "right",
      width: 190,
      sortKey: "finalized_at",
      clientSorter: (left, right) =>
        compareInvoices(left, right, (a, b) => timestampCompare(a.finalizedAtIso, b.finalizedAtIso)),
      render: (_, row) => {
        if (row.kind === "invoice") return <NumberText>{row.finalizedAt}</NumberText>;
        if (row.kind === "meter") return <NumberText>{row.quantity}</NumberText>;
        if (row.kind === "line") return <div><NumberText>{row.line.quantity}</NumberText>{row.line.quantityBasis && <p className="field-hint" style={{ overflowWrap: "anywhere" }}>{row.line.quantityBasis}</p>}</div>;
        return null;
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
      sortKey: "total_amount",
      clientSorter: (left, right) =>
        compareInvoices(left, right, (a, b) => a.totalAmountValue - b.totalAmountValue),
      render: (_, row) => {
        if (row.kind === "invoice") return <NumberText strong>{row.totalAmount}</NumberText>;
        if (row.kind === "meter") return <NumberText strong>{row.amount}</NumberText>;
        if (row.kind === "amount") return <NumberText type="secondary">{row.amount}</NumberText>;
        return <NumberText strong>{row.line.amount}</NumberText>;
      },
    },
  ];
  const { columns, onChange } = useTableSort(query, capabilities, sortableColumns);

  return (
    <>
    <div className="data-mobile-records">{groups.map(group => <article key={group.id} className="data-mobile-record"><div className="data-mobile-line"><h3>{group.customerName}</h3><strong className="numeric">{group.totalAmount}</strong></div><p className="field-hint">{group.month} / {group.finalizedAt} KST 확정</p><details><summary>확정 계산 내역</summary>{linesOf(group.items).map((line, index) => <div key={index}><div className="data-mobile-line"><span>{line.label}</span><strong className="numeric">{line.amount}</strong></div><p className="field-hint">수량 {line.quantity} / 단가 {line.unitPrice}</p>{line.quantityBasis && <p className="field-hint" style={{ overflowWrap: "anywhere" }}>{line.quantityBasis}</p>}{Object.keys(line.dimensionValues).length > 0 && <p className="field-hint">{Object.entries(line.dimensionValues).map(([key, value]) => `${key}: ${String(value)}`).join(", ")}</p>}</div>)}<div className="data-mobile-line"><span>공급가액</span><span className="numeric">{group.supplyAmount}</span></div><div className="data-mobile-line"><span>세액</span><span className="numeric">{group.taxAmount}</span></div></details></article>)}<div className="data-mobile-total"><span>세금 포함 합계</span><span className="numeric">{totalAmount}</span></div></div>
    <div className="data-desktop-table"><Table<InvoiceRow>
      rowKey="key"
      columns={columns}
      dataSource={rows}
      onChange={onChange}
      pagination={false}
      size="medium"
      sticky={TABLE_STICKY}
      tableLayout="fixed"
      scroll={{ x: TABLE_MIN_WIDTH }}
      expandable={{ expandedRowKeys, onExpand, indentSize: 16, expandIcon: INVOICE_EXPAND_ICON }}
      onRow={(row) =>
        row.kind === "invoice" || row.kind === "meter"
          ? { onClick: () => toggle(row.key), style: { cursor: "pointer" } }
          : {}
      }
      summary={() => (
        <Table.Summary.Row>
          <Table.Summary.Cell index={0}>
            <Typography.Text strong>합계</Typography.Text>
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
      )}
    /></div>
    </>
  );
}
