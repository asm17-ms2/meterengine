"use client";

import { Alert, Card, Space, Tag, Typography } from "antd";

import { ErrorViewAlert } from "@/components/ErrorViewAlert";
import type { ErrorView } from "@/lib/meter-engine";

export type TargetStatus =
  | { name: string; ok: true; customer_count: number }
  | { name: string; ok: false; error: ErrorView };

type OverviewPanelProps = {
  targets: TargetStatus[];
  configError?: string;
  bridgeMessage: string;
};

function TargetCard({ target }: { target: TargetStatus }) {
  if (target.ok) {
    return (
      <Card size="small" title={target.name} extra={<Tag color="green">연결됨</Tag>}>
        고객 {target.customer_count}명
      </Card>
    );
  }
  return (
    <Card size="small" title={target.name} extra={<Tag color="red">실패</Tag>}>
      <ErrorViewAlert error={target.error} />
    </Card>
  );
}

export function OverviewPanel({ targets, configError, bridgeMessage }: OverviewPanelProps) {
  return (
    <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
      <Typography.Title level={3}>개요</Typography.Title>
      {configError === undefined ? null : <Alert type="error" title="대상 설정을 읽지 못했습니다" description={configError} />}
      {targets.map((target) => (
        <TargetCard key={target.name} target={target} />
      ))}
      <Typography.Text>{bridgeMessage}</Typography.Text>
    </Space>
  );
}
