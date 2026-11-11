import type { Metadata } from "next";
import Link from "next/link";
import { AuthFrame } from "@/components/auth/AuthFrame";

export const metadata: Metadata = { title: "가입 신청 접수" };

export default function PendingPage() {
  return <AuthFrame currentStep={2} title="가입 신청을 접수했어요" description="가입이 승인되면 신청한 이메일과 비밀번호로 로그인할 수 있어요."><div className="pending-note"><h2>승인됐는지는 로그인해 보세요</h2><p>승인되면 로그인할 때 바로 콘솔로 이동해요. 승인 전이거나 승인되지 않았으면 콘솔을 이용할 수 없다는 안내가 나와요.</p></div><Link className="primary-link" href="/login">로그인 화면으로</Link></AuthFrame>;
}
