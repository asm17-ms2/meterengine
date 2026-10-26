"use client";

import { Button, Descriptions, Drawer, theme, Typography } from "antd";
import { useRef } from "react";

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
