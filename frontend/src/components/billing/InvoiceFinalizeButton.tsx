"use client";

import { Button, Flex } from "antd";
import { useId } from "react";
import { useTenantScope } from "@/components/auth/TenantAccess";

export function InvoiceFinalizeButton({
  disabled = false,
  disabledReason,
  onClick,
}: {
  disabled?: boolean;
  disabledReason?: string;
  onClick?: () => void;
}) {
  const hintId = useId();
  const canManage = useTenantScope("admin");
  if (!canManage) return null;
  // 비활성인 이유를 툴팁이 아닌 보이는 글자로 두고 버튼이 그 글자를 설명으로 가리킨다.
  if (disabled && disabledReason) {
    return (
      <Flex vertical gap={4}>
        <Button type="primary" disabled aria-describedby={hintId}>
          월 전체 확정
        </Button>
        <span id={hintId} className="field-hint">{disabledReason}</span>
      </Flex>
    );
  }
  return (
    <Button type="primary" disabled={disabled} onClick={onClick}>
      월 전체 확정
    </Button>
  );
}
