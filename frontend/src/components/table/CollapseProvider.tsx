"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";

type CollapseMode = "default" | "expanded" | "collapsed";

type CollapseState = {
  isCollapsed: (id: string, collapsedByDefault?: boolean) => boolean;
  toggle: (id: string) => void;
  expandAll: () => void;
  collapseAll: () => void;
};

const CollapseContext = createContext<CollapseState | null>(null);

export function CollapseProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<CollapseMode>("default");
  const [flippedIds, setFlippedIds] = useState<ReadonlySet<string>>(new Set());

  const isCollapsed = useCallback(
    (id: string, collapsedByDefault = false) =>
      (mode === "default" ? collapsedByDefault : mode === "collapsed") !== flippedIds.has(id),
    [mode, flippedIds],
  );

  const toggle = useCallback((id: string) => {
    setFlippedIds((prev) => {
      const nextFlippedIds = new Set(prev);
      if (!nextFlippedIds.delete(id)) nextFlippedIds.add(id);
      return nextFlippedIds;
    });
  }, []);

  const expandAll = useCallback(() => {
    setMode("expanded");
    setFlippedIds(new Set());
  }, []);

  const collapseAll = useCallback(() => {
    setMode("collapsed");
    setFlippedIds(new Set());
  }, []);

  const value = useMemo(
    () => ({ isCollapsed, toggle, expandAll, collapseAll }),
    [isCollapsed, toggle, expandAll, collapseAll],
  );

  return <CollapseContext value={value}>{children}</CollapseContext>;
}

export function useCollapse(): CollapseState {
  const value = useContext(CollapseContext);
  if (!value) throw new Error("CollapseProvider 안에서만 쓸 수 있다");
  return value;
}

export type CollapsibleRow = {
  key: string;
  isCollapsedByDefault?: boolean;
  children?: readonly CollapsibleRow[];
};

export function useExpandedRowKeys<Row extends CollapsibleRow>(rows: readonly Row[]) {
  const { isCollapsed, toggle } = useCollapse();
  const expandedRowKeys: string[] = [];
  const visit = (row: CollapsibleRow) => {
    if (!row.children?.length) return;
    if (!isCollapsed(row.key, row.isCollapsedByDefault ?? false)) expandedRowKeys.push(row.key);
    row.children.forEach(visit);
  };
  rows.forEach(visit);
  return {
    expandedRowKeys,
    onExpand: (_isExpanded: boolean, row: Row) => toggle(row.key),
    toggle,
  };
}
