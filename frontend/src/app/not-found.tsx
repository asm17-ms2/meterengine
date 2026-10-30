import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "페이지를 찾을 수 없음" };

export default function NotFound() {
  return <main id="main-content" className="access-page"><h1>페이지를 찾을 수 없습니다</h1><p>주소가 바뀌었거나 없는 화면입니다. 주소를 확인하세요.</p><div className="access-actions"><Link href="/">홈으로</Link><Link href="/organizations">도입사 선택으로</Link></div></main>;
}
