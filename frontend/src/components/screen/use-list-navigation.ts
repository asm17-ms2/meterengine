import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useSyncExternalStore } from "react";

import type { ListCapabilities } from "@/lib/api/capabilities";
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
  const searchParams = useSearchParams();
  const committedSearch = searchParams.toString();
  const urlText = searchParams.get("q") ?? query.q;
  const pending = useSyncExternalStore(subscribePendingQuery, readPendingQuery, readNoPendingQuery);
  const isPendingHere =
    pending !== null && pending.pathname === pathname && pending.committedSearch === committedSearch;
  const committedQuery = useMemo(() => ({ ...query, q: urlText }), [query, urlText]);
  const liveQuery = isPendingHere ? pending.query : committedQuery;

  useEffect(() => {
    const current = readPendingQuery();
    if (current && (current.pathname !== pathname || current.committedSearch !== committedSearch)) {
      setPendingQuery(null);
    }
  }, [pathname, committedSearch]);

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
