"use client";

import Link from "next/link";
import { Button } from "antd";

export default function ErrorPage({ error }: { error: Error & { digest?: string } }) {
  return <main id="main-content" className="access-page"><h1>화면을 불러오지 못했어요</h1><p>잠시 뒤 다시 시도해 주세요.{error.digest ? ` 오류 코드: ${error.digest}` : ""}</p><div className="access-actions"><Button onClick={() => window.location.reload()}>다시 시도</Button><Link href="/login">로그인 화면으로</Link></div></main>;
}
