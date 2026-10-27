"use client";

import { Button, Descriptions, Drawer, theme, Typography } from "antd";
import { useRef } from "react";

import { ScopedLink } from "@/components/workspace/ScopedLink";

import type { EventRowView } from "@/components/events/EventsTable";
import { useInitialFocus } from "@/components/screen/use-initial-focus";
import { DRAWER_SIZE } from "@/components/table/table-defaults";

export function EventDetailDrawer({
  row,
  isOpen,
  onClose,
}: {
  row: EventRowView;
  isOpen: boolean;
  onClose: () => void;
}) {
  const { token } = theme.useToken();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useInitialFocus(closeButtonRef, isOpen);

  return (
    <Drawer
      open={isOpen}
      title="이벤트 상세"
      size={DRAWER_SIZE}
      closable={false}
      extra={
        <Button ref={closeButtonRef} onClick={onClose}>
          닫기
        </Button>
      }
      onClose={onClose}
    >
      <Descriptions
        column={1}
        size="small"
        bordered
        items={[
          {
            key: "transactionId",
            label: "transaction_id",
            children: (
              <Typography.Text style={{ fontFamily: token.fontFamilyCode, wordBreak: "break-all" }}>
                {row.transactionId}
              </Typography.Text>
            ),
          },
          { key: "customerName", label: "고객", children: row.customerName },
          { key: "eventType", label: "type", children: row.eventType },
          { key: "occurredAt", label: "occurred_at", children: row.occurredAt },
          { key: "receivedAt", label: "received_at", children: row.receivedAt },
          ...(row.requestId ? [{ key: "requestId", label: "수집 요청", children: <ScopedLink href={`/audit?q=${encodeURIComponent(row.requestId)}`}>이 요청의 감사 기록 보기</ScopedLink> }] : []),
          ...(row.actorType ? [{ key: "actor", label: "전송 주체", children: `${row.actorType === "api_key" ? "API 키" : row.actorType === "user_account" ? "로그인 사용자" : row.actorType}${row.actorId ? ` (${row.actorId})` : ""}` }] : []),
        ]}
      />
      <Typography.Title level={5} style={{ marginTop: token.marginLG }}>
        원본 JSON
      </Typography.Title>
      <Typography>
        <pre>{row.rawJson}</pre>
      </Typography>
    </Drawer>
  );
}
