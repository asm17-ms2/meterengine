import type { Metadata } from "next";
import { AntdRegistry } from "@ant-design/nextjs-registry";

import { AppProviders } from "@/components/shell/AppProviders";
import "./globals.css";

export const metadata: Metadata = {
  title: "MeterEngine 관리자 콘솔",
  description: "사용량 이벤트와 고객별 집계를 확인하는 도입사 운영자 화면",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>
        <AntdRegistry>
          <AppProviders>{children}</AppProviders>
        </AntdRegistry>
      </body>
    </html>
  );
}
