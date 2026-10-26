"use client";

import { Button } from "antd";

export function InvoiceFinalizeButton({
  disabled = false,
  title,
  onClick,
}: {
  disabled?: boolean;
  title?: string;
  onClick?: () => void;
}) {
  return (
    <Button type="primary" disabled={disabled} title={title} onClick={onClick}>
      인보이스 확정
    </Button>
  );
}
