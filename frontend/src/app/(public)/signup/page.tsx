import type { Metadata } from "next";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { AuthForm } from "@/components/auth/AuthForm";
import { config } from "@/lib/config";

export const metadata: Metadata = { title: "가입" };
// Turnstile 사이트 키를 빌드 때가 아니라 서버를 띄울 때의 환경 변수에서 읽는다.
export const dynamic = "force-dynamic";

export default function SignupPage() {
  return <AuthFrame currentStep={1} title="가입" description="이메일을 인증하면 바로 테스트할 수 있어요."><AuthForm mode="signup" turnstileSiteKey={config.turnstileSiteKey} /></AuthFrame>;
}
