"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";

type CollapseMode = "expanded" | "collapsed";

type CollapseState = {
  isCollapsed: (id: string) => boolean;
  toggle: (id: string) => void;
  expandAll: () => void;
  collapseAll: () => void;
};

const CollapseContext = createContext<CollapseState | null>(null);

export function CollapseProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<CollapseMode>("expanded");
  const [flippedIds, setFlippedIds] = useState<ReadonlySet<string>>(new Set());

  const isCollapsed = useCallback(
    (id: string) => (mode === "collapsed") !== flippedIds.has(id),
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
