"use client";

import { MonthField } from "@/components/screen/MonthField";
import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";
import { readEnvironment } from "@/lib/environment";

export function WorkspaceHeader({ title, description, children }: { title: string; description: string; children?: React.ReactNode }) {
  return <div className="workspace-heading"><div><h1>{title}</h1><p>{description}</p></div>{children && <div className="workspace-actions">{children}</div>}</div>;
}

export function MonthFilter({ month, preserve = ["customer_id", "environment", "organization_id"] }: { month: string; preserve?: string[] }) {
  const search = useWorkspaceSearch();
  const names = [...new Set(["organization_id", "environment", ...preserve])];
  return <form method="get" className="workspace-actions">{names.map(name => {
    const value = name === "environment" ? readEnvironment(search.get(name)) : search.get(name);
    return value ? <input key={name} type="hidden" name={name} value={value} /> : null;
  })}<MonthField key={month} month={month} label="청구 월" /><button className="month-apply" type="submit">조회</button></form>;
}
