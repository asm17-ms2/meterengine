import type { Metadata } from "next";
import localFont from "next/font/local";
import { AntdRegistry } from "@ant-design/nextjs-registry";

import { AppProviders } from "@/components/shell/AppProviders";
import "./globals.css";

const pretendard = localFont({
  src: "./fonts/PretendardVariable.woff2",
  variable: "--font-pretendard",
  weight: "100 900",
  display: "swap",
  fallback: ["Apple SD Gothic Neo", "Malgun Gothic", "sans-serif"],
});

// 워드마크와 코드 글꼴도 실어 보내 OS마다 다른 글꼴로 바뀌지 않게 한다. 라틴 글자만 담아 한글은 Pretendard로 넘긴다
const wordmarkFont = localFont({ src: "./fonts/NunitoSansVariable.woff2", variable: "--font-wordmark", weight: "200 1000", display: "swap" });
const monoFont = localFont({ src: "./fonts/JetBrainsMonoVariable.woff2", variable: "--font-mono", weight: "100 800", display: "swap" });

export const metadata: Metadata = {
  title: { default: "MeterEngine 콘솔", template: "%s | MeterEngine" },
  description: "고객별 사용량과 요금, 청구와 결제를 관리하는 MeterEngine 콘솔",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={`${pretendard.variable} ${wordmarkFont.variable} ${monoFont.variable}`}>
      <body>
        <a href="#main-content" className="skip-link">본문으로 이동</a>
        <AntdRegistry>
          <AppProviders>{children}</AppProviders>
        </AntdRegistry>
      </body>
    </html>
  );
}
