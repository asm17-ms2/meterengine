"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname } from "next/navigation";

type FilterOption = { value: string; label: string };
const ARCHIVE_OPTIONS: FilterOption[] = [{ value: "active", label: "사용 중" }, { value: "archived", label: "보관됨" }, { value: "all", label: "전체" }];

export function ArchiveFilter({ label = "관리 상태", parameterName = "status", defaultValue = "active", options = ARCHIVE_OPTIONS }: { label?: string; parameterName?: string; defaultValue?: string; options?: readonly FilterOption[] }) {
  const pathname = usePathname();
  const search = useWorkspaceSearch();
  const requestedValues = search.getAll(parameterName);
  const requested = requestedValues.length === 1 ? requestedValues[0] : undefined;
  const current = options.some(option => option.value === requested) ? requested : defaultValue;
  return <nav className="archive-filter" aria-label={label}>{options.map(item => {
    const next = new URLSearchParams(search.toString());
    next.set(parameterName, item.value);
    next.delete("page");
    return <Link key={item.value} href={`${pathname}?${next}`} scroll={false} aria-current={current === item.value ? "page" : undefined}>{item.label}</Link>;
  })}</nav>;
}
