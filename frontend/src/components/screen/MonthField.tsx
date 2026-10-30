"use client";

import { useId, useState } from "react";
import { MonthPicker } from "@/components/screen/MonthPicker";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { readListQuery } from "@/lib/list-query";

export function MonthField({ month, label, name = "month" }: { month: string; label: string; name?: string }) {
  const id = useId();
  const [draft, setDraft] = useState(month);
  return <div className="workspace-field"><label htmlFor={id}>{label}</label><MonthPicker id={id} label={label} query={readListQuery({ month: draft }, CAPABILITIES.listEvents, { hasMonth: true })} capabilities={CAPABILITIES.listEvents} month={draft} onChange={setDraft} /><input type="hidden" name={name} value={draft} /></div>;
}
