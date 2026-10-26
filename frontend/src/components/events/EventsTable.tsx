"use client";

import { Flex, Table, theme, Typography } from "antd";
import { useState } from "react";

import { EventDetailDrawer } from "@/components/events/EventDetailDrawer";
import { ListPagination } from "@/components/screen/ListPagination";
import { ScreenNote } from "@/components/screen/ScreenNote";
import { NumberText } from "@/components/table/NumberText";
import { TABLE_STICKY } from "@/components/table/table-defaults";
import { type SortableColumn, useTableSort } from "@/components/table/table-sort";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export type EventRowView = {
  transactionId: string;
  customerName: string;
  eventType: string;
  occurredAt: string;
  receivedAt: string;
  propertiesPreview: string;
  rawJson: string;
};

const MIN_WIDTH = 940;

export function EventsTable({
  rows,
  query,
  total,
  note,
}: {
  rows: EventRowView[];
  query: ListQuery;
  total: number;
  note: string;
}) {
  const { token } = theme.useToken();
  const capabilities = CAPABILITIES.listEvents;
  const [selected, setSelected] = useState<EventRowView | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  function openDetail(row: EventRowView) {
    setSelected(row);
    setIsDrawerOpen(true);
  }

  const codeCell = () => ({ style: { fontFamily: token.fontFamilyCode } });

  const sortableColumns: SortableColumn<EventRowView>[] = [
    {
      key: "transactionId",
      title: "transaction_id",
      width: 150,
      ellipsis: true,
      onCell: codeCell,
      render: (_, row) => (
        <Typography.Link
          role="button"
          tabIndex={0}
          style={{ fontFamily: token.fontFamilyCode }}
          onClick={(event) => {
            event.stopPropagation();
            openDetail(row);
          }}
        >
          {row.transactionId}
        </Typography.Link>
      ),
    },
    {
      key: "occurredAt",
      title: "occurred_at",
      width: 160,
      sortKey: "occurred_at",
      render: (_, row) => <NumberText>{row.occurredAt}</NumberText>,
    },
    {
      key: "receivedAt",
      title: "received_at",
      width: 160,
      render: (_, row) => <NumberText type="secondary">{row.receivedAt}</NumberText>,
    },
    { key: "customerName", title: "고객", dataIndex: "customerName", width: 110, ellipsis: true },
    { key: "eventType", title: "type", dataIndex: "eventType", width: 140, ellipsis: true },
    {
      key: "propertiesPreview",
      title: "properties",
      dataIndex: "propertiesPreview",
      ellipsis: true,
      onCell: () => ({
        style: {
          fontFamily: token.fontFamilyCode,
          fontSize: token.fontSizeSM,
          color: token.colorTextSecondary,
        },
      }),
    },
  ];

  const { columns, onChange } = useTableSort(query, capabilities, sortableColumns);

  return (
    <>
      <Table<EventRowView>
        rowKey="transactionId"
        columns={columns}
        dataSource={rows}
        onChange={onChange}
        pagination={false}
        size="medium"
        sticky={TABLE_STICKY}
        tableLayout="fixed"
        scroll={{ x: MIN_WIDTH }}
        onRow={(row) => ({
          style: { cursor: "pointer" },
          onClick: () => openDetail(row),
          onKeyDown: (event) => {
            if (event.key !== "Enter" && event.key !== " ") return;
            event.preventDefault();
            openDetail(row);
          },
        })}
      />

      <Flex justify="space-between" align="center" gap="medium" wrap>
        <ScreenNote>{note}</ScreenNote>
        <ListPagination query={query} capabilities={capabilities} total={total} />
      </Flex>

      {selected ? (
        <EventDetailDrawer
          row={selected}
          isOpen={isDrawerOpen}
          onClose={() => setIsDrawerOpen(false)}
        />
      ) : null}
    </>
  );
}
