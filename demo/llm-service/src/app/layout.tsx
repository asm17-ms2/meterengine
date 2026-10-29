import type { Metadata } from "next";
import localFont from "next/font/local";
import { FontProvider } from "@/components/FontProvider";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import "./globals.css";

const pretendard = localFont({ src: "./fonts/PretendardVariable.woff2", variable: "--font-pretendard", weight: "100 900", display: "swap", fallback: ["Apple SD Gothic Neo", "Malgun Gothic", "sans-serif"] });

export const metadata: Metadata = {
  title: { default: "Lumen | 나의 AI 작업 공간", template: "%s | Lumen" },
  description: "대화를 만들고 사용량과 청구를 확인하는 Lumen 고객 서비스 예제",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko" className={pretendard.variable}><body><AntdRegistry><FontProvider><a href="#main-content" className="skip-link">본문으로 이동</a>{children}</FontProvider></AntdRegistry></body></html>;
}
