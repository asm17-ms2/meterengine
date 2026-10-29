"use client";

import { Button } from "antd";
import { useTenantScope } from "@/components/auth/TenantAccess";

export function InvoiceFinalizeButton({
  disabled = false,
  title,
  onClick,
}: {
  disabled?: boolean;
  title?: string;
  onClick?: () => void;
}) {
  const canManage = useTenantScope("admin");
  if (!canManage) return null;
  return (
    <Button type="primary" disabled={disabled} title={title} onClick={onClick}>
      월 전체 확정
    </Button>
  );
}
