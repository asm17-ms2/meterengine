"use client";

import { Button } from "antd";
import { useRouter } from "next/navigation";

export function RetryButton() {
  const router = useRouter();
  return (
    <Button type="primary" onClick={() => router.refresh()}>
      다시 시도
    </Button>
  );
}
