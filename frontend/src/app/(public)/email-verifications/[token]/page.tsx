import type { Metadata } from "next";
import Link from "next/link";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { EmailVerificationConfirmForm } from "@/components/auth/EmailVerificationConfirmForm";
import { isEmailVerificationToken } from "@/lib/auth/email-verification";

export const metadata: Metadata = { title: "이메일 인증", robots: { index: false, follow: false }, referrer: "no-referrer" };

// 메일 보안 검사기가 링크를 미리 열어도 토큰이 쓰이지 않게, 화면을 연 사람이 버튼을 눌러야 인증한다.
export default async function EmailVerificationPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!isEmailVerificationToken(token)) return <AuthFrame currentStep={1} title="이메일 인증" description="올바른 인증 링크가 아니에요. 메일 속 링크를 그대로 열었는지 확인해 주세요."><Link className="primary-link" href="/email-verifications">인증 메일 다시 받기</Link></AuthFrame>;
  return <AuthFrame currentStep={1} title="이메일 인증" description="버튼을 누르면 이메일 인증이 끝나요."><EmailVerificationConfirmForm token={token} /></AuthFrame>;
}
