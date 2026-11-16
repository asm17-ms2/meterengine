"use client";

import { LoadingOutlined } from "@ant-design/icons";
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
    colorText: "#202b40", colorTextSecondary: "#5a6578", colorTextDescription: "#5a6578", colorTextPlaceholder: "#5a6578", colorLinkHover: "#48339a", colorBgLayout: "#f3f5f9", colorBorder: "#dce1eb", fontFamily: 'var(--font-pretendard), "Apple SD Gothic Neo", "Malgun Gothic", sans-serif', fontFamilyCode: "var(--font-mono), var(--font-pretendard), monospace", fontSize: 14, borderRadius: 6, controlHeight: 38,
  },
  components: { Tag: { green7: "#237804", gold7: "#874d00", orange7: "#ad4e00" }, Input: { colorBorder: "#7c8799" }, Checkbox: { colorBorder: "#7c8799" }, Descriptions: { labelColor: "#5a6578" }, Select: { colorBorder: "#7c8799" }, ["DatePicker"]: { colorBorder: "#7c8799" }, Table: { headerBg: "#f7f8fc", headerColor: "#5a6578", cellPaddingBlock: 15 }, Card: { boxShadow: "none" } },
};

// 기본 로딩 아이콘은 퇴장 애니메이션이 끝나지 않으면 버튼 이름에 "loading"을 남긴다. 직접 지정하면 애니메이션 없이 로딩 동안만 그린다.
const BUTTON_CONFIG = { loadingIcon: <LoadingOutlined aria-hidden /> };

export function AppProviders({ children }: { children: React.ReactNode }) { return <ConfigProvider locale={koreanLocale} theme={CONSOLE_THEME} button={BUTTON_CONFIG}><App>{children}</App></ConfigProvider>; }
