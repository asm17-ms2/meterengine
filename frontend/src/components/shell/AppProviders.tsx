"use client";

import { App, ConfigProvider, type ThemeConfig } from "antd";
import koreanLocale from "antd/locale/ko_KR";
import dayjs from "dayjs";
import "dayjs/locale/ko";

dayjs.locale("ko");

const CONSOLE_THEME: ThemeConfig = {
  components: {
    Layout: { headerBg: "#ffffff", headerPadding: "0 24px" },
  },
};

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <ConfigProvider locale={koreanLocale} theme={CONSOLE_THEME}>
      <App>{children}</App>
    </ConfigProvider>
  );
}
