"use client";

import { CloseCircleFilled } from "@ant-design/icons";
import { AutoComplete } from "antd";
import { useEffect, useRef, useState } from "react";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export function TypeFilter({
  query,
  capabilities,
  eventTypes,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  eventTypes: string[];
}) {
  const { query: liveQuery, push } = useListNavigation(query, capabilities);
  const [text, setText] = useState(liveQuery.type ?? "");
  const [syncedType, setSyncedType] = useState(liveQuery.type);
  const committedTypeRef = useRef(liveQuery.type);
  const textRef = useRef(liveQuery.type ?? "");
  const isClearedRef = useRef(false);
  const isEnterPendingRef = useRef(false);

  if (liveQuery.type !== syncedType) {
    setSyncedType(liveQuery.type);
    setText(liveQuery.type ?? "");
  }

  useEffect(() => {
    committedTypeRef.current = liveQuery.type;
    textRef.current = liveQuery.type ?? "";
  }, [liveQuery.type]);

  if (!capabilities.type) return null;

  function commit(nextText: string) {
    const nextType = nextText.trim() === "" ? undefined : nextText.trim();
    if (nextType === committedTypeRef.current) return;
    committedTypeRef.current = nextType;
    push({ type: nextType });
  }

  function changeText(nextText: string) {
    textRef.current = nextText;
    isClearedRef.current = false;
    setText(nextText);
  }

  return (
    <AutoComplete
      aria-label="type"
      allowClear={{
        clearIcon: <CloseCircleFilled aria-hidden aria-label={undefined} />,
        label: "지우기",
      }}
      placeholder="type: 전체"
      options={eventTypes.map((eventType) => ({ value: eventType }))}
      showSearch={{
        filterOption: (input, option) =>
          String(option?.value ?? "").toLowerCase().includes(input.toLowerCase()),
      }}
      value={text}
      onChange={changeText}
      onSelect={(eventType: string) => {
        isEnterPendingRef.current = false;
        changeText(eventType);
        commit(eventType);
      }}
      onClear={() => {
        changeText("");
        isClearedRef.current = true;
        commit("");
      }}
      onBlur={() => {
        if (isClearedRef.current) {
          isClearedRef.current = false;
          return;
        }
        commit(textRef.current);
      }}
      onInputKeyDown={(event) => {
        if (event.key !== "Enter") return;
        isEnterPendingRef.current = true;
        setTimeout(() => {
          if (!isEnterPendingRef.current) return;
          isEnterPendingRef.current = false;
          commit(textRef.current);
        }, 0);
      }}
      style={{ minWidth: 200 }}
    />
  );
}
