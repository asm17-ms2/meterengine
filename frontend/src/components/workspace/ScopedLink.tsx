"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { workspaceHref } from "@/lib/workspace-href";

export function ScopedLink({ href, children, inheritFilters = false, ...props }: Omit<React.ComponentProps<typeof Link>, "href"> & { href: string; inheritFilters?: boolean }) {
  const pathname = usePathname();
  const search = useWorkspaceSearch().toString();
  const destination = href.startsWith("?") ? pathname + href : href;
  return <Link href={workspaceHref(destination, search, { inheritFilters })} {...props}>{children}</Link>;
}
