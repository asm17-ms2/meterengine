"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";
import { workspaceHref } from "@/lib/workspace-href";

import { Button, type ButtonProps } from "antd";
import { usePathname, useRouter } from "next/navigation";

export function LinkButton({
  href,
  replace = false,
  inheritFilters = false,
  onClick,
  ...buttonProps
}: Omit<ButtonProps, "href"> & { href: string; replace?: boolean; inheritFilters?: boolean }) {
  const router = useRouter();
  const pathname = usePathname();
  const scopedHref = workspaceHref(href.startsWith("?") ? pathname + href : href, useWorkspaceSearch(), { inheritFilters });

  function handleClick(event: React.MouseEvent<HTMLElement>) {
    onClick?.(event);
    if (event.defaultPrevented) return;
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (replace) router.replace(scopedHref);
    else router.push(scopedHref);
  }

  return <Button {...buttonProps} href={scopedHref} onClick={handleClick} />;
}
