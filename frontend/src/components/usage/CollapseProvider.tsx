"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";

/**
 * 고객 그룹 접힘 상태.
 *
 * 필터 행의 '모두 펼치기 / 모두 접기' 버튼과 표의 그룹 행이 같은 상태를 봐야 하는데,
 * 필터 행은 로딩 중에도 보여야 해서 표를 감싸는 Suspense 밖에 있다. 그래서 상태를
 * 둘보다 위로 올린다. 컨텍스트는 Suspense 경계를 넘어 전달된다.
 *
 * 아이디 집합 대신 (기본 모드 + 뒤집힌 것들)로 들고 있는 이유는, '모두 접기' 버튼이
 * 고객 목록을 몰라도 되게 하기 위해서다. 그 버튼은 데이터가 도착하기 전에도 눌릴 수 있다.
 */
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
  const [flipped, setFlipped] = useState<ReadonlySet<string>>(new Set());

  const isCollapsed = useCallback(
    (id: string) => (mode === "collapsed") !== flipped.has(id),
    [mode, flipped],
  );

  const toggle = useCallback((id: string) => {
    setFlipped((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }, []);

  const expandAll = useCallback(() => {
    setMode("expanded");
    setFlipped(new Set());
  }, []);

  const collapseAll = useCallback(() => {
    setMode("collapsed");
    setFlipped(new Set());
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

/** 필터 행 오른쪽에 붙는 두 버튼. */
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
