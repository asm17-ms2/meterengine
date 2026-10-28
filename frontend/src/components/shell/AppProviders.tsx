"use client";

import { App, ConfigProvider, type ThemeConfig } from "antd";
import koreanLocale from "antd/locale/ko_KR";
import dayjs from "dayjs";
import "dayjs/locale/ko";

dayjs.locale("ko");

const CONSOLE_THEME: ThemeConfig = {
  token: {
    colorPrimary: "#5746ad", colorPrimaryBorder: "#5746ad", colorInfo: "#5746ad", colorSuccess: "#087f75", colorWarning: "#9a620c", colorError: "#c23d4b",
    colorInfoBg: "#f3f0fb", colorInfoBgHover: "#eeeafb", colorInfoBorder: "#c8bde7",
    colorSuccessBg: "#f5fbf8", colorSuccessBgHover: "#f0faf6", colorSuccessBorder: "#b3d5c6",
    colorWarningBg: "#fff8e9", colorWarningBgHover: "#fff4dd", colorWarningBorder: "#e7cd97",
    colorErrorBg: "#fff4f3", colorErrorBgHover: "#fff1f0", colorErrorBorder: "#edc5c8",
    colorText: "#202b40", colorTextSecondary: "#5a6578", colorTextDescription: "#5a6578", colorTextPlaceholder: "#5a6578", colorBgLayout: "#f3f5f9", colorBorder: "#dce1eb", fontFamily: 'var(--font-pretendard), "Apple SD Gothic Neo", "Malgun Gothic", sans-serif', fontSize: 14, borderRadius: 6, controlHeight: 38,
  },
  components: { Tag: { green7: "#237804", gold7: "#874d00", orange7: "#ad4e00" }, Input: { colorBorder: "#7c8799" }, Select: { colorBorder: "#7c8799" }, ["DatePicker"]: { colorBorder: "#7c8799" }, Layout: { headerBg: "#ffffff", headerPadding: "0 24px" }, Menu: { itemSelectedBg: "#eeeafb", itemSelectedColor: "#48339a", itemMarginInline: 10, itemBorderRadius: 6 }, Table: { headerBg: "#f7f8fc", headerColor: "#5a6578", cellPaddingBlock: 15 }, Card: { boxShadow: "none" } },
};

export function AppProviders({ children }: { children: React.ReactNode }) { return <ConfigProvider locale={koreanLocale} theme={CONSOLE_THEME}><App>{children}</App></ConfigProvider>; }
