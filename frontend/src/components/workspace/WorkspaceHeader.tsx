"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";
import { readEnvironment } from "@/lib/environment";

export function WorkspaceHeader({ title, description, kicker, children }: { title: string; description: string; kicker?: string; children?: React.ReactNode }) {
  return <div className="workspace-heading"><div>{kicker && <span className="workspace-kicker">{kicker}</span>}<h1>{title}</h1><p>{description}</p></div>{children}</div>;
}

export function MonthFilter({ month, preserve = ["customer_id", "environment", "organization_id"] }: { month: string; preserve?: string[] }) {
  const search = useWorkspaceSearch();
  const names = [...new Set(["organization_id", "environment", ...preserve])];
  return <form method="get" className="workspace-actions">{names.map(name => {
    const value = name === "environment" ? readEnvironment(search.get(name)) : search.get(name);
    return value ? <input key={name} type="hidden" name={name} value={value} /> : null;
  })}<label className="workspace-field"><span>청구 월</span><input aria-label="청구 월" type="month" name="month" defaultValue={month} required pattern="[0-9]{4}-(0[1-9]|1[0-2])" placeholder="YYYY-MM" title="YYYY-MM 형식으로 입력하세요(예: 2026-09)" /></label><button className="month-apply" type="submit">조회</button></form>;
}
