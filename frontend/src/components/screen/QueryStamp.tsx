"use client";

import { Typography } from "antd";

export function QueryStamp({ text, label = "조회 시각" }: { text: string; label?: string }) {
  return (
    <Typography.Text type="secondary">
      {label} {text}
    </Typography.Text>
  );
}
