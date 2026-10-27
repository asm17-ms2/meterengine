"use client";

import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Descriptions, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { useRouter } from "next/navigation";
import { useTransition } from "react";

import { formatKstTimestamp } from "@/lib/event-form";
import type { WebhookReceipt } from "@/lib/webhook-receipt";

type WebhookRow = WebhookReceipt & { key: string };

type WebhookListProps = {
  targetName: string;
  environmentLabel: string;
  receiveUrl: string;
  hasSecret: boolean;
  receipts: WebhookReceipt[];
  loadError?: string;
};

function VerdictTag({ row }: { row: WebhookRow }) {
  if (row.verified) {
    return <Tag color="green">유효</Tag>;
  }
  return <Tag color="red">무효 {row.reason}</Tag>;
}

const COLUMNS: TableColumnsType<WebhookRow> = [
  {
    title: "받은 시각",
    dataIndex: "received_at",
    width: 200,
    render: (receivedAt: string) => formatKstTimestamp(new Date(receivedAt)),
  },
  { title: "webhook-id", dataIndex: "webhook_id", render: (id: string | null) => id ?? "-" },
  { title: "type", dataIndex: "type", render: (type: string | null) => type ?? "-" },
  { title: "결과", key: "verdict", render: (_value: unknown, row: WebhookRow) => <VerdictTag row={row} /> },
];

function toRows(receipts: WebhookReceipt[]): WebhookRow[] {
  const seen = new Map<string, number>();
  return receipts.map((receipt) => {
    const base = `${receipt.received_at} ${receipt.webhook_id ?? ""}`;
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    return { ...receipt, key: count === 0 ? base : `${base} ${count}` };
  });
}

function PayloadView({ payload }: { payload: string }) {
  return <pre style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-all" }}>{payload}</pre>;
}

export function WebhookList({ targetName, environmentLabel, receiveUrl, hasSecret, receipts, loadError }: WebhookListProps) {
  const router = useRouter();
  const [isRefreshing, startRefresh] = useTransition();
  const rows = toRows(receipts);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr)", gap: 16, minWidth: 0 }}>
      <Typography.Title level={3}>웹훅 수신</Typography.Title>
      <div style={{ minWidth: 0, overflowX: "auto" }}><Descriptions
        size="small"
        column={1}
        bordered
        items={[
          { key: "target", label: "연동 대상", children: `${targetName} / ${environmentLabel}` },
          { key: "url", label: "수신 URL", children: <Typography.Text copyable>{receiveUrl}</Typography.Text> },
          {
            key: "secret",
            label: "서명 비밀값",
            children: hasSecret ? <Tag color="green">설정됨</Tag> : <Tag color="red">설정 안 됨</Tag>,
          },
        ]}
      /></div>
      {hasSecret ? null : (
        <Alert type="warning" title="이 대상의 서명 비밀값이 없습니다" description="서버의 대상 설정에 서명 비밀값을 등록하세요. 다른 대상의 비밀값은 대신 사용하지 않습니다." />
      )}
      {loadError === undefined ? null : <Alert type="error" title="수신 기록을 읽지 못했습니다" description={loadError} />}
      <Space>
        <Button icon={<ReloadOutlined />} loading={isRefreshing} onClick={() => startRefresh(() => router.refresh())}>
          새로고침
        </Button>
        <Typography.Text type="secondary">최근 {rows.length}건</Typography.Text>
      </Space>
      <div style={{ minWidth: 0, overflowX: "auto" }}><Table<WebhookRow>
        size="small"
        columns={COLUMNS}
        dataSource={rows}
        pagination={{ pageSize: 20 }}
        expandable={{ expandedRowRender: (row) => <PayloadView payload={row.payload} /> }}
      /></div>
    </div>
  );
}
