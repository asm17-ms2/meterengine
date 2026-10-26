"use client";

import { Button, type ButtonProps } from "antd";
import { useRouter } from "next/navigation";

export function LinkButton({
  href,
  replace = false,
  onClick,
  ...buttonProps
}: Omit<ButtonProps, "href"> & { href: string; replace?: boolean }) {
  const router = useRouter();

  function handleClick(event: React.MouseEvent<HTMLElement>) {
    onClick?.(event);
    if (event.defaultPrevented) return;
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (replace) router.replace(href);
    else router.push(href);
  }

  return <Button {...buttonProps} href={href} onClick={handleClick} />;
}
