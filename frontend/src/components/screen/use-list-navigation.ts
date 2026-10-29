import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useLayoutEffect, useMemo, useRef, useSyncExternalStore } from "react";

import type { ListCapabilities } from "@/lib/api/capabilities";
import { readMonth } from "@/lib/month";
import {
  applyListOverrides,
  buildListHref,
  type ListQuery,
  type ListQueryOverrides,
} from "@/lib/list-query";

type PendingQuery = { pathname: string; committedSearch: string; query: ListQuery };

let pendingQuery: PendingQuery | null = null;
const pendingListeners = new Set<() => void>();

function setPendingQuery(next: PendingQuery | null) {
  pendingQuery = next;
  pendingListeners.forEach((listener) => listener());
}

function subscribePendingQuery(listener: () => void) {
  pendingListeners.add(listener);
  return () => {
    pendingListeners.delete(listener);
  };
}

function readPendingQuery() {
  return pendingQuery;
}

function readNoPendingQuery() {
  return null;
}

export function useListNavigation(query: ListQuery, capabilities: ListCapabilities) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useWorkspaceSearch();
  const committedSearch = searchParams.toString();
  const urlText = searchParams.get("q") ?? query.q;
  const organizationId = query.organizationId || searchParams.get("organization_id") || undefined;
  const month = query.month ?? (searchParams.get("month") ? readMonth(searchParams.get("month")!) : undefined);
  const pending = useSyncExternalStore(subscribePendingQuery, readPendingQuery, readNoPendingQuery);
  const renderedQueryRef = useRef(query);
  const isPendingHere =
    pending !== null && pending.pathname === pathname && pending.committedSearch === committedSearch;
  const committedQuery = useMemo(() => ({ ...query, q: urlText, organizationId, month }), [query, urlText, organizationId, month]);
  const liveQuery = isPendingHere ? pending.query : committedQuery;

  // 같은 주소로 끝난 이동도 다음 입력 전에 임시 필터를 지운다.
  useLayoutEffect(() => {
    const isNewRender = renderedQueryRef.current !== query;
    renderedQueryRef.current = query;
    const current = readPendingQuery();
    if (
      current &&
      (isNewRender || current.pathname !== pathname || current.committedSearch !== committedSearch)
    ) {
      setPendingQuery(null);
    }
  }, [query, pathname, committedSearch]);

  const navigate = useCallback(
    (overrides: ListQueryOverrides, mode: "push" | "replace") => {
      const nextQuery = applyListOverrides(liveQuery, overrides);
      const href = buildListHref(pathname, nextQuery);
      setPendingQuery({ pathname, committedSearch, query: nextQuery });
      if (mode === "push") router.push(href);
      else router.replace(href, { scroll: false });
    },
    [router, pathname, committedSearch, liveQuery],
  );

  const push = useCallback((overrides: ListQueryOverrides) => navigate(overrides, "push"), [navigate]);

  const replace = useCallback(
    (overrides: ListQueryOverrides) => navigate(overrides, "replace"),
    [navigate],
  );

  return { query: liveQuery, capabilities, push, replace };
}
