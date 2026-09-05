"use client";

import { useState } from "react";

import { EventDetailDrawer } from "@/components/events/EventDetailDrawer";
import { GridCell, GridHead, GridRowButton, GridTable } from "@/components/table/Grid";

/**
 * 화면이 그리는 뷰모델. 시각과 JSON 문자열은 서버에서 다 만들어 온다.
 * 클라이언트에서 Intl을 부르면 브라우저 시간대가 KST가 아닐 때 서버와 값이 달라져
 * 하이드레이션이 어긋난다.
 */
export type EventRowView = {
  transactionId: string;
  customerName: string;
  eventType: string;
  /** `2026-08-09 14:11:02` (KST). */
  occurredAt: string;
  receivedAt: string;
  /** properties를 한 줄로 줄인 것. 칸이 좁아 말줄임된다. */
  propertiesPreview: string;
  /** 드로어의 <pre>에 그대로 들어가는 정렬된 JSON. */
  rawJson: string;
};

/*
 * 디자인의 열 너비(118 / 148 / 148 / 104 / 148)를 실제 값에 맞춰 넓혔다. 프로토타입의
 * 목 데이터가 짧아서 좁게 잡혀 있었다.
 *   - transaction_id: 118px에서는 'mock-2026-08...'까지만 보여 모든 행이 같아 보인다.
 *     행을 구분하는 유일한 값이라 잘리면 표가 쓸모없다. 그래도 고객이 보내는 값이라
 *     UUID처럼 긴 것이 오면 잘린다. 전체 값은 드로어에 있다.
 *   - occurred_at / received_at: 148px에서 '2026-08-28 15:47:06'이 두 줄로 접혀
 *     행 높이가 두 배가 됐다.
 *   - 고객: 104px에서 '아크메 주식회사'가 잘렸다.
 */
const COLUMNS = "170px 165px 165px 130px 140px minmax(0, 1fr)";
const MIN_WIDTH = 1000;

const HEAD = [
  "transaction_id",
  "occurred_at",
  "received_at",
  "고객",
  "event_type",
  "properties",
] as const;

export function EventsTable({ rows }: { rows: EventRowView[] }) {
  const [selected, setSelected] = useState<EventRowView | null>(null);

  return (
    <>
      <GridTable minWidth={MIN_WIDTH}>
        <GridHead columns={COLUMNS} labels={HEAD} />
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
