import type { Metadata } from "next";
import localFont from "next/font/local";
import { DemoBanner } from "@/components/DemoBanner";
import { FontProvider } from "@/components/FontProvider";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import { COMPANY } from "@/lib/catalog";
import "./globals.css";

const pretendard = localFont({ src: "./fonts/PretendardVariable.woff2", variable: "--font-pretendard", weight: "100 900", display: "swap", fallback: ["Apple SD Gothic Neo", "Malgun Gothic", "sans-serif"] });

export const metadata: Metadata = {
  title: { default: `${COMPANY.name} | 대화, API, 일괄 처리`, template: `%s | ${COMPANY.name}` },
  description: `${COMPANY.name} 모델로 대화하고 API와 일괄 처리를 시험하며 토큰 사용량과 청구를 확인하는 연동 예제 서비스`,
  robots: { index: false, follow: false },
};

// 데모 띠는 루트 레이아웃에 둔다. 앱 화면뿐 아니라 Next 기본 404 화면에도 붙는다.
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko" className={pretendard.variable}><body><AntdRegistry><FontProvider><a href="#main-content" className="skip-link">본문으로 이동</a><div className="app-frame"><DemoBanner /><div className="app-scroll" data-scroll-root="">{children}</div></div></FontProvider></AntdRegistry></body></html>;
}
