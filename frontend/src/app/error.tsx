"use client";

import Link from "next/link";
import { Button } from "antd";

export default function ErrorPage() {
  return <main id="main-content" className="access-page"><h1>화면을 불러오지 못했습니다</h1><p>서버 연결과 로그인 상태를 확인한 뒤 다시 시도하세요.</p><div className="access-actions"><Button onClick={() => window.location.reload()}>다시 시도</Button><Link href="/login">로그인 화면으로</Link></div></main>;
}
