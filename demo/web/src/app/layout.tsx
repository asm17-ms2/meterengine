import { AntdRegistry } from "@ant-design/nextjs-registry";
import { Layout } from "antd";
import { Suspense } from "react";
import type { Metadata } from "next";
import localFont from "next/font/local";
import { FontProvider } from "@/components/FontProvider";

import { AppMenu } from "@/components/AppMenu";
import "./globals.css";

const pretendard = localFont({ src: "./fonts/PretendardVariable.woff2", variable: "--font-pretendard", weight: "100 900", display: "swap", fallback: ["Apple SD Gothic Neo", "Malgun Gothic", "sans-serif"] });

export const metadata: Metadata = {
  title: "MeterEngine 테스트 웹",
  description: "SDK로 이벤트를 보내고 백엔드 판정을 보는 로컬 테스트 화면",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" className={pretendard.variable}>
      <body style={{ margin: 0 }}>
        <AntdRegistry><FontProvider>
          <a className="skip-link" href="#main-content">본문으로 이동</a>
          <Layout hasSider style={{ minHeight: "100vh" }}>
            <Suspense fallback={null}><AppMenu /></Suspense>
            <main id="main-content" className="demo-main" style={{ flex: 1, minWidth: 0, padding: 24 }}>{children}</main>
          </Layout>
        </FontProvider></AntdRegistry>
      </body>
    </html>
  );
}
