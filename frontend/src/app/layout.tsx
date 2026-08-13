import type { Metadata } from "next";
import { Archivo } from "next/font/google";
import "./globals.css";

// Modernist 디자인 시스템의 본문/제목 서체. 라틴 문자와 숫자를 그린다.
// modernist.css의 --font-heading / --font-body가 이 변수를 가리킨다.
const archivo = Archivo({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-archivo",
});

// Archivo에 한글 글리프가 없어서 한글은 Pretendard가 그린다.
// 가변 동적 subset판을 쓴다. 브라우저가 unicode-range를 보고 실제로 쓰는 조각만
// 받으므로(조각당 약 31KB), 정적 풀셋(굵기당 약 750KB)보다 훨씬 가볍다.
// 버전을 고정해 CDN 쪽 변경이 화면에 새어 들어오지 않게 한다.
const PRETENDARD_CSS =
  "https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css";

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
    <html lang="ko" className={`${archivo.variable} h-full antialiased`}>
      <head>
        <link rel="preconnect" href="https://cdn.jsdelivr.net" crossOrigin="" />
        <link rel="stylesheet" href={PRETENDARD_CSS} crossOrigin="" />
      </head>
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
