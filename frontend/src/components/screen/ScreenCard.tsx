"use client";

import { Card } from "antd";

export function ScreenCard({ children }: { children: React.ReactNode }) {
  return (
    <Card
      variant="outlined"
      styles={{ body: { display: "flex", flexDirection: "column", gap: 16 } }}
    >
      {children}
    </Card>
  );
}
