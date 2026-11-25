"use client";

import { focusInFrames } from "@/components/screen/use-initial-focus";
import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

// 상세는 주소의 transaction_id만 바꿔 여닫는다. 라우터로 이동하면 목록, 건수, 유형, 월 조회가 다시 돈다.
export function useEventDetail() {
  const search = useWorkspaceSearch();
  const hrefFor = (transactionId?: string) => {
    const next = new URLSearchParams(search);
    if (transactionId) next.set("transaction_id", transactionId);
    else next.delete("transaction_id");
    return `/events?${next}`;
  };
  return {
    openId: search.get("transaction_id"),
    hrefFor,
    open: (transactionId: string) => window.history.pushState(null, "", hrefFor(transactionId)),
    // 닫은 뒤 이 서랍을 연 표의 행 링크로 포커스를 돌린다. 주소로 연 이벤트가 목록에 없으면 아무것도 하지 않는다.
    close: (transactionId: string) => {
      window.history.replaceState(null, "", hrefFor());
      focusInFrames(() => Array.from(document.querySelectorAll<HTMLElement>("[data-transaction-id]")).find(link => link.dataset.transactionId === transactionId && link.offsetParent !== null));
    },
  };
}
