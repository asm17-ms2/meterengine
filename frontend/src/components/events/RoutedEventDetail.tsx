"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { useRouter } from "next/navigation";
import { EventDetailDrawer } from "@/components/events/EventDetailDrawer";
import type { EventRowView } from "@/components/events/EventsTable";

export function RoutedEventDetail({ row }: { row: EventRowView }) {
  const router = useRouter();
  const search = useWorkspaceSearch();
  return <EventDetailDrawer row={row} isOpen onClose={() => { const next = new URLSearchParams(search.toString()); next.delete("transaction_id"); router.replace(`/events?${next}`, { scroll: false }); }} />;
}
