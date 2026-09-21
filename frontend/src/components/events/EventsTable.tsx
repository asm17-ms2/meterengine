"use client";

import { useState } from "react";

import { EventDetailDrawer } from "@/components/events/EventDetailDrawer";
import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRowButton } from "@/components/table/GridRowButton";
import { GridTable } from "@/components/table/GridTable";

export type EventRowView = {
  transactionId: string;
  customerName: string;
  eventType: string;
  /** `2026-08-09 14:11:02` (KST). */
  occurredAt: string;
  receivedAt: string;
  propertiesPreview: string;
  /** 드로어의 <pre>에 그대로 들어가는 정렬된 JSON. */
  rawJson: string;
};

const COLUMNS = "170px 165px 165px 130px 140px minmax(0, 1fr)";
const MIN_WIDTH = 1000;

const HEAD_LABELS = [
  "transaction_id",
  "occurred_at",
  "received_at",
  "고객",
  "type",
  "properties",
] as const;

export function EventsTable({ rows }: { rows: EventRowView[] }) {
  const [selected, setSelected] = useState<EventRowView | null>(null);

  return (
    <>
      <GridTable minWidth={MIN_WIDTH}>
        <GridHead columns={COLUMNS} labels={HEAD_LABELS} />
        {rows.map((row) => (
          <GridRowButton
            key={row.transactionId}
            columns={COLUMNS}
            onClick={() => setSelected(row)}
          >
            <GridCell className="grid-cell--mono grid-cell--truncate">
              {row.transactionId}
            </GridCell>
            <GridCell className="grid-cell--num">{row.occurredAt}</GridCell>
            <GridCell className="grid-cell--num grid-cell--muted">
              {row.receivedAt}
            </GridCell>
            <GridCell className="grid-cell--truncate">{row.customerName}</GridCell>
            <GridCell className="grid-cell--truncate">{row.eventType}</GridCell>
            <GridCell className="grid-cell--props grid-cell--truncate">
              {row.propertiesPreview}
            </GridCell>
          </GridRowButton>
        ))}
      </GridTable>

      {selected ? (
        <EventDetailDrawer row={selected} onClose={() => setSelected(null)} />
      ) : null}
    </>
  );
}
