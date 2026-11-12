"use client";

import { Button, type ButtonProps } from "antd";
import { useRouter } from "next/navigation";

export function RetryButton({ children = "다시 시도", type = "primary", size }: { children?: React.ReactNode; type?: ButtonProps["type"]; size?: ButtonProps["size"] }) {
  const router = useRouter();
  return (
    <Button type={type} size={size} onClick={() => router.refresh()}>
      {children}
    </Button>
  );
}
