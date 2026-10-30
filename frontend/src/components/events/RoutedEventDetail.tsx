"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { useRouter } from "next/navigation";
import { EventDetailDrawer } from "@/components/events/EventDetailDrawer";
import { focusInFrames } from "@/components/screen/use-initial-focus";
import type { EventRowView } from "@/components/events/EventsTable";

export function RoutedEventDetail({ row }: { row: EventRowView }) {
  const router = useRouter();
  const search = useWorkspaceSearch();
  // 닫은 뒤 이 서랍을 연 표의 행 링크로 포커스를 돌린다. 다른 페이지에서 딥링크로 열었으면 그 행이 없어 아무것도 하지 않는다.
  return <EventDetailDrawer row={row} isOpen onClose={() => {
    const next = new URLSearchParams(search.toString());
    next.delete("transaction_id");
    router.replace(`/events?${next}`, { scroll: false });
    focusInFrames(() => Array.from(document.querySelectorAll<HTMLElement>("[data-transaction-id]")).find(link => link.dataset.transactionId === row.transactionId && link.offsetParent !== null));
  }} />;
}
