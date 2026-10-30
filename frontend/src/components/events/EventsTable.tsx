"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { Flex, Table, theme, Typography } from "antd";
import { useTransition } from "react";
import { useRouter } from "next/navigation";

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
  requestId?: string;
  actorType?: string;
  actorId?: string;
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
  const router = useRouter();
  const searchParams = useWorkspaceSearch();
  const [isOpening, startOpening] = useTransition();

  function detailHref(row: EventRowView) { const next = new URLSearchParams(searchParams.toString()); next.set("transaction_id", row.transactionId); return `/events?${next}`; }

  function openDetail(row: EventRowView) { startOpening(() => router.push(detailHref(row), { scroll: false })); }
  // 수정 키가 없으면 페이지를 다시 읽지 않고 서랍을 연다. 데스크톱 행과 모바일 카드가 같이 쓴다.
  function openOnPlainClick(event: React.MouseEvent, row: EventRowView) {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    openDetail(row);
  }

  const codeCell = () => ({ style: { fontFamily: token.fontFamilyCode } });

  const sortableColumns: SortableColumn<EventRowView>[] = [
    {
      key: "transactionId",
      title: "전송 ID",
      sortKey: "transaction_id",
      width: 150,
      ellipsis: true,
      onCell: codeCell,
      render: (_, row) => (
        <Typography.Link
          href={detailHref(row)}
          data-transaction-id={row.transactionId}
          style={{ fontFamily: token.fontFamilyCode }}
          onClick={(event) => {
            event.stopPropagation();
            openOnPlainClick(event, row);
          }}
        >
          {row.transactionId}
        </Typography.Link>
      ),
    },
    {
      key: "occurredAt",
      title: "발생 시각 (KST)",
      width: 160,
      sortKey: "occurred_at",
      render: (_, row) => <NumberText>{row.occurredAt}</NumberText>,
    },
    {
      key: "receivedAt",
      title: "수신 시각 (KST)",
      sortKey: "received_at",
      width: 160,
      render: (_, row) => <NumberText type="secondary">{row.receivedAt}</NumberText>,
    },
    { key: "customerName", sortKey: "customer_name", title: "고객", dataIndex: "customerName", width: 110, ellipsis: true },
    { key: "eventType", sortKey: "type", title: "이벤트 유형", dataIndex: "eventType", width: 140, ellipsis: true },
    {
      key: "propertiesPreview",
      title: "전송 속성",
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
      {isOpening && <p role="status" className="field-hint">이벤트 상세를 불러오고 있습니다.</p>}
      <div className="data-mobile-records">{rows.map(row => <article key={row.transactionId} className="data-mobile-record"><h3><a href={detailHref(row)} data-transaction-id={row.transactionId} onClick={event => openOnPlainClick(event, row)}>{row.transactionId}</a></h3><div className="data-mobile-line"><span>{row.customerName}</span><strong>{row.eventType}</strong></div><p className="field-hint">발생 {row.occurredAt} KST</p><p className="field-hint">수신 {row.receivedAt} KST</p><code className="workspace-cell-text">{row.propertiesPreview}</code></article>)}</div>
      <div className="data-desktop-table"><Table<EventRowView>
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
            if (event.target instanceof HTMLAnchorElement) return;
            if (event.key !== "Enter" && event.key !== " ") return;
            event.preventDefault();
            openDetail(row);
          },
        })}
      /></div>

      <Flex justify="space-between" align="center" gap="medium" wrap>
        <ScreenNote>{note}</ScreenNote>
        <ListPagination query={query} capabilities={capabilities} total={total} />
      </Flex>


    </>
  );
}
