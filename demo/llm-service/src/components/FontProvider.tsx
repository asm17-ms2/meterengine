"use client";

import { ConfigProvider } from "antd";

export function FontProvider({ children }: { children: React.ReactNode }) {
  return <ConfigProvider theme={{ token: { fontFamily: 'var(--font-pretendard), "Apple SD Gothic Neo", "Malgun Gothic", sans-serif' } }}>{children}</ConfigProvider>;
}
