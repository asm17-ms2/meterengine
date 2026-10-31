"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { workspaceHref } from "@/lib/workspace-href";
import { environmentReadyPath } from "@/lib/environment";

export function useScopedHref(href: string, options: { inheritFilters?: boolean } = {}) {
  const pathname = usePathname();
  const search = useWorkspaceSearch().toString();
  return workspaceHref(href.startsWith("?") ? pathname + href : href, search, options);
}

export function ScopedLink({ href, children, inheritFilters = false, ...props }: Omit<React.ComponentProps<typeof Link>, "href"> & { href: string; inheritFilters?: boolean }) {
  const scopedHref = useScopedHref(href, { inheritFilters });
  const path = scopedHref.split(/[?#]/, 1)[0];
  return <Link href={scopedHref} {...props} prefetch={environmentReadyPath(path) ? false : props.prefetch}>{children}</Link>;
}
