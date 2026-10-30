"use client";

import { ConfigProvider } from "antd";

export function FontProvider({ children }: { children: React.ReactNode }) {
  return <ConfigProvider theme={{ token: { colorPrimary: "#146c94", colorText: "#17344f", colorTextSecondary: "#526879", colorTextDescription: "#526879", colorTextPlaceholder: "#526879", fontFamily: 'var(--font-pretendard), "Apple SD Gothic Neo", "Malgun Gothic", sans-serif' }, components: { Tag: { green7: "#237804", gold7: "#874d00", orange7: "#ad4e00" }, Input: { colorBorder: '#7c8799' }, Select: { colorBorder: '#7c8799' }, ["DatePicker"]: { colorBorder: '#7c8799' } } }}>{children}</ConfigProvider>;
}
