import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

const SEARCH_URL_DELAY_MS = 300;

export type SearchText = {
  text: string;
  setText: (text: string) => void;
  flush: () => void;
  clear: () => void;
};

function toSearchHref(pathname: string, currentSearch: string, searchText: string): string {
  const params = new URLSearchParams(currentSearch);
  if (searchText === "") params.delete("q");
  else params.set("q", searchText);
  const search = params.toString();
  return search ? `${pathname}?${search}` : pathname;
}

export function useSearchText(query: ListQuery, capabilities: ListCapabilities): SearchText {
  const { replace } = useListNavigation(query, capabilities);
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const urlText = (searchParams.get("q") ?? "").trim();
  const [text, setText] = useState(urlText);
  const [seenUrlText, setSeenUrlText] = useState(urlText);
  const [writtenText, setWrittenText] = useState(urlText);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  if (urlText !== seenUrlText) {
    setSeenUrlText(urlText);
    if (urlText !== writtenText) {
      setWrittenText(urlText);
      setText(urlText);
    }
  }

  const writeUrl = useCallback(
    (searchText: string) => {
      clearTimeout(timerRef.current);
      setWrittenText(searchText);
      if (capabilities.q) {
        replace({ q: searchText || undefined });
        return;
      }
      window.history.replaceState(null, "", toSearchHref(pathname, window.location.search, searchText));
    },
    [capabilities.q, replace, pathname],
  );

  useEffect(() => {
    if (!capabilities.q) return;
    const normalized = text.trim();
    if (normalized === writtenText) return;
    const timer = setTimeout(() => writeUrl(normalized), SEARCH_URL_DELAY_MS);
    timerRef.current = timer;
    return () => clearTimeout(timer);
  }, [capabilities.q, text, writtenText, writeUrl]);

  const changeText = (nextText: string) => {
    setText(nextText);
    if (capabilities.q) return;
    const normalized = nextText.trim();
    if (normalized !== writtenText) writeUrl(normalized);
  };

  const flush = () => {
    const normalized = text.trim();
    if (normalized !== writtenText) writeUrl(normalized);
  };

  const clear = () => {
    setText("");
    if (writtenText !== "") writeUrl("");
    else clearTimeout(timerRef.current);
  };

  return { text, setText: changeText, flush, clear };
}
