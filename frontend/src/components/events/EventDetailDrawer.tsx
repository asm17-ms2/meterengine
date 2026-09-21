"use client";

import { useEffect, useRef } from "react";

import type { EventRowView } from "@/components/events/EventsTable";

export function EventDetailDrawer({
  row,
  onClose,
}: {
  row: EventRowView;
  onClose: () => void;
}) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeButtonRef.current?.focus();
  }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} />
      <aside
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="event-detail-title"
      >
        <div className="drawer__header">
          <h4 id="event-detail-title" style={{ margin: 0 }}>
            이벤트 상세
          </h4>
          <button
            ref={closeButtonRef}
            type="button"
            className="btn btn-secondary"
            style={{ padding: "6px 10px" }}
            onClick={onClose}
          >
            닫기
          </button>
        </div>

        <div className="drawer__body">
          <dl className="detail-grid">
            <dt>transaction_id</dt>
            <dd className="detail-grid__mono">{row.transactionId}</dd>
            <dt>고객</dt>
            <dd>{row.customerName}</dd>
            <dt>type</dt>
            <dd>{row.eventType}</dd>
            <dt>occurred_at</dt>
            <dd>{row.occurredAt}</dd>
            <dt>received_at</dt>
            <dd>{row.receivedAt}</dd>
          </dl>

          <div>
            <div className="drawer__section-label">원본 JSON</div>
            <pre className="raw-json">{row.rawJson}</pre>
          </div>
        </div>
      </aside>
    </>
  );
}
