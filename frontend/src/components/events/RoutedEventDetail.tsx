"use client";

import type { ReactNode } from "react";

import { EventDetailDrawer } from "@/components/events/EventDetailDrawer";
import type { EventRowView } from "@/components/events/EventsTable";
import { useEventDetail } from "@/components/events/use-event-detail";

// 서랍을 닫아도 서버가 화면을 다시 그리지 않아, 서버가 그린 상세와 오류는 주소가 그 이벤트를 가리킬 때만 보인다.
export function WhenEventSelected({ transactionId, children }: { transactionId: string; children: ReactNode }) {
  return useEventDetail().openId === transactionId ? children : null;
}

export function RoutedEventDetail({ row }: { row: EventRowView }) {
  const detail = useEventDetail();
  return <EventDetailDrawer row={row} isOpen onClose={() => detail.close(row.transactionId)} />;
}
