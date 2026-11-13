import type { Metadata } from "next";
import Link from "next/link";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { EmailVerificationResendForm } from "@/components/auth/EmailVerificationResendForm";
import { config } from "@/lib/config";

export const metadata: Metadata = { title: "이메일 인증" };

// Turnstile 사이트 키를 빌드 때가 아니라 서버를 띄울 때의 환경 변수에서 읽는다.
export const dynamic = "force-dynamic";

export default async function EmailVerificationsPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const isFromLogin = (await searchParams).reason === "unverified";
  return <AuthFrame currentStep={2} title={isFromLogin ? "이메일 인증이 남았어요" : "메일함을 확인하세요"} description={isFromLogin ? "로그인하려면 가입한 이메일로 받은 인증 메일의 링크를 열어 인증을 마치세요." : "가입한 이메일로 인증 메일을 보냈어요. 메일 속 링크를 열면 인증이 끝나요."}>
    <div className="pending-note"><h2>메일이 오지 않았나요?</h2><p>스팸함을 먼저 확인해 보세요. 그래도 없으면 가입한 이메일을 입력하고 다시 받아 보세요.</p></div>
    <EmailVerificationResendForm turnstileSiteKey={config.turnstileSiteKey} />
    <Link className="primary-link" href="/login">로그인 화면으로</Link>
  </AuthFrame>;
}
