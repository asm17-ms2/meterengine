"use client";

import { useEffect, useRef } from "react";

import type { EventRowView } from "@/components/events/EventTable";

/**
 * 이벤트 한 건의 상세. 오른쪽에서 덮는 패널이다.
 *
 * 표의 properties 칸은 한 줄로 잘려서 긴 값이 보이지 않는다. 그걸 펼쳐 보는 자리다.
 * 별도 라우트로 만들지 않은 이유: 목록의 스크롤 위치와 페이지를 유지한 채 열려야 한다.
 */
export function EventDetailDrawer({
  row,
  onClose,
}: {
  row: EventRowView;
  onClose: () => void;
}) {
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    // 열자마자 포커스를 패널 안으로 옮긴다. 그러지 않으면 방금 누른 표의 행에
    // 포커스가 남아, Tab이 패널이 아니라 뒤쪽 목록을 훑는다.
    closeRef.current?.focus();
  }, []);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
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
            ref={closeRef}
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
            <dt>event_type</dt>
            <dd>{row.eventType}</dd>
            <dt>occurred_at</dt>
            <dd>{row.occurredAt}</dd>
            <dt>received_at</dt>
            <dd>{row.receivedAt}</dd>
          </dl>

          <div>
            {/*
              디자인의 라벨은 'properties (raw)'인데 그 아래 블록은 properties만이
              아니라 이벤트 전체를 담고 있다. 내용 쪽을 남기고 라벨을 고쳤다.
              위 정의 목록이 고객 '이름'과 읽기 좋게 다듬은 시각을 보여주는 반면,
              이 블록은 고객 UUID 전체와 오프셋이 붙은 원본 시각을 그대로 보여준다.
            */}
            <div className="drawer__section-label">원본 JSON</div>
            <pre className="raw-json">{row.rawJson}</pre>
          </div>
        </div>
      </aside>
    </>
  );
}
