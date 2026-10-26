"use client";

import { Tag } from "antd";

export function StatusTag({ color, children }: { color?: string; children: React.ReactNode }) {
  return (
    <Tag color={color} variant="outlined">
      {children}
    </Tag>
  );
}
