import { AntdRegistry } from "@ant-design/nextjs-registry";
import { Layout } from "antd";
import { Suspense } from "react";
import type { Metadata } from "next";

import { AppMenu } from "@/components/AppMenu";
import "./globals.css";

export const metadata: Metadata = {
  title: "MeterEngine 테스트 웹",
  description: "SDK로 이벤트를 보내고 백엔드 판정을 보는 로컬 테스트 화면",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body style={{ margin: 0 }}>
        <AntdRegistry>
          <a className="skip-link" href="#main-content">본문으로 이동</a>
          <Layout hasSider style={{ minHeight: "100vh" }}>
            <Suspense fallback={null}><AppMenu /></Suspense>
            <main id="main-content" className="demo-main" style={{ flex: 1, minWidth: 0, padding: 24 }}>{children}</main>
          </Layout>
        </AntdRegistry>
      </body>
    </html>
  );
}
