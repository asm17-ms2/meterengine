"use client";

import { useScopedHref } from "@/components/workspace/ScopedLink";

import { Button, type ButtonProps } from "antd";
import { useRouter } from "next/navigation";

export function LinkButton({
  href,
  replace = false,
  inheritFilters = false,
  onClick,
  ...buttonProps
}: Omit<ButtonProps, "href"> & { href: string; replace?: boolean; inheritFilters?: boolean }) {
  const router = useRouter();
  const scopedHref = useScopedHref(href, { inheritFilters });

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
