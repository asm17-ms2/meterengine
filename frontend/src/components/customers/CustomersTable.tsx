"use client";

import { Button, Flex, Table, theme, Typography } from "antd";

import { NumberText } from "@/components/table/NumberText";
import { TABLE_STICKY, koreanCompare, timestampCompare } from "@/components/table/table-defaults";
import {
  type ClientSortSelectionState,
  type SortableColumn,
  useTableSort,
} from "@/components/table/table-sort";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export type CustomerRowView = {
  id: string;
  name: string;
  billingEmail: string | null;
  /** `2026-08-14` (KST). */
  createdAt: string;
  createdAtIso: string;
};

const MIN_WIDTH = 940;

function compareCreatedAt(left: CustomerRowView, right: CustomerRowView): number {
  return timestampCompare(left.createdAtIso, right.createdAtIso);
}

export function CustomersTable({
  rows,
  query,
  capabilities,
  onEdit,
  onDelete,
  clientSortSelection,
}: {
  rows: CustomerRowView[];
  query: ListQuery;
  capabilities: ListCapabilities;
  onEdit: (row: CustomerRowView) => void;
  onDelete: (row: CustomerRowView) => void;
  clientSortSelection?: ClientSortSelectionState;
}) {
  const { token } = theme.useToken();

  const sortableColumns: SortableColumn<CustomerRowView>[] = [
    {
      key: "name",
      title: "고객명",
      ellipsis: true,
      sortKey: "name",
      clientSorter: (left, right) => koreanCompare(left.name, right.name),
      render: (_, row) => <Typography.Text strong>{row.name}</Typography.Text>,
    },
    {
      key: "billingEmail",
      title: "청구 이메일",
      ellipsis: true,
      sortKey: "billing_email",
      clientSorter: (left, right) =>
        koreanCompare(left.billingEmail ?? "", right.billingEmail ?? ""),
      render: (_, row) => (
        <Typography.Text type="secondary">{row.billingEmail ?? ""}</Typography.Text>
      ),
    },
    {
      key: "id",
      title: "고객 ID",
      width: 320,
      ellipsis: true,
      render: (_, row) => (
        <Typography.Text type="secondary" style={{ fontFamily: token.fontFamilyCode }}>
          {row.id}
        </Typography.Text>
      ),
    },
    {
      key: "createdAt",
      title: "등록일",
      width: 130,
      sortKey: "created_at",
      clientSorter: compareCreatedAt,
      render: (_, row) => <NumberText type="secondary">{row.createdAt}</NumberText>,
    },
    {
      key: "actions",
      title: "작업",
      width: 100,
      align: "right",
      fixed: "end",
      render: (_, row) => (
        <Flex justify="end">
          <Button
            type="link"
            size="small"
            aria-label={`${row.name} 수정`}
            onClick={() => onEdit(row)}
          >
            수정
          </Button>
          <Button
            type="link"
            size="small"
            danger
            aria-label={`${row.name} 삭제`}
            onClick={() => onDelete(row)}
          >
            삭제
          </Button>
        </Flex>
      ),
    },
  ];

  const { columns, onChange } = useTableSort(query, capabilities, sortableColumns, {
    clientSortSelection,
  });

  return (
    <Table<CustomerRowView>
      rowKey="id"
      columns={columns}
      dataSource={rows}
      onChange={onChange}
      pagination={false}
      size="medium"
      sticky={TABLE_STICKY}
      scroll={{ x: MIN_WIDTH }}
    />
  );
}
