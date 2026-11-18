"use client";

import { theme, Typography } from "antd";

export function ScreenNote({ children }: { children: React.ReactNode }) {
  const { token } = theme.useToken();
  return (
    <Typography.Paragraph style={{ margin: 0, fontSize: token.fontSizeSM }}>
      {children}
    </Typography.Paragraph>
  );
}
