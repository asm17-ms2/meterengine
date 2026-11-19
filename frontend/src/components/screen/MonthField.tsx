"use client";

import { useCallback, useId, useState } from "react";
import { MonthPicker } from "@/components/screen/MonthPicker";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { isMonth } from "@/lib/month";
import { readListQuery } from "@/lib/list-query";

export function MonthField({ month, label, name = "month" }: { month: string; label: string; name?: string }) {
  const id = useId();
  const [draft, setDraft] = useState(month);
  // React가 입력 이벤트를 듣기 전에 적은 월도 폼에 반영한다.
  const preserveTypedMonth = useCallback((field: HTMLDivElement | null) => {
    const input = field?.querySelector<HTMLInputElement>(`#${CSS.escape(id)}`);
    if (input && input.value !== month && isMonth(input.value)) setDraft(input.value);
  }, [id, month]);
  return <div ref={preserveTypedMonth} className="workspace-field"><label htmlFor={id}>{label}</label><MonthPicker id={id} label={label} query={readListQuery({ month: draft }, CAPABILITIES.listEvents, { hasMonth: true })} capabilities={CAPABILITIES.listEvents} month={draft} onChange={setDraft} /><input type="hidden" name={name} value={draft} /></div>;
}
