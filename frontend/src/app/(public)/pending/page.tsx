import type { Metadata } from "next";
import Link from "next/link";
import { AuthFrame } from "@/components/auth/AuthFrame";

export const metadata: Metadata = { title: "가입 신청 접수" };

export default function PendingPage() {
  return <AuthFrame title="가입 신청을 접수했습니다" description="운영자가 도입사를 승인하면 신청한 이메일과 비밀번호로 로그인할 수 있습니다."><div className="pending-note"><h2>승인 여부는 로그인해 확인하세요</h2><p>승인 전에 로그인하면 사용 가능한 도입사가 없다는 안내가 표시됩니다. 승인된 뒤에는 도입사 업무 화면으로 이동합니다.</p><p>이 화면은 승인 상태를 실시간으로 표시하지 않습니다.</p></div><Link className="primary-link" href="/login">로그인 화면으로</Link></AuthFrame>;
}
