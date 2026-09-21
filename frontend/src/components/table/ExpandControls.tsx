"use client";

import { useCollapse } from "@/components/table/CollapseProvider";

export function ExpandControls() {
  const { expandAll, collapseAll } = useCollapse();
  return (
    <div className="filter-bar__actions">
      <button
        type="button"
        className="btn btn-ghost"
        style={{ fontSize: 12.5 }}
        onClick={expandAll}
      >
        모두 펼치기
      </button>
      <button
        type="button"
        className="btn btn-ghost"
        style={{ fontSize: 12.5 }}
        onClick={collapseAll}
      >
        모두 접기
      </button>
    </div>
  );
}
