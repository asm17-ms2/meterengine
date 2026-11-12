import type { Metadata } from "next";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { AuthForm } from "@/components/auth/AuthForm";
import { config } from "@/lib/config";

export const metadata: Metadata = { title: "도입사 가입 신청" };
// Turnstile 사이트 키를 빌드 때가 아니라 서버를 띄울 때의 환경 변수에서 읽는다.
export const dynamic = "force-dynamic";

export default function SignupPage() {
  return <AuthFrame currentStep={1} title="도입사 가입 신청" description="도입사와 담당자 정보를 등록하세요. 운영자 승인 후 로그인할 수 있습니다."><AuthForm mode="signup" turnstileSiteKey={config.turnstileSiteKey} /></AuthFrame>;
}
