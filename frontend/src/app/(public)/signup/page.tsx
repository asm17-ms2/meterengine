import { AuthFrame } from "@/components/auth/AuthFrame";
import { AuthForm } from "@/components/auth/AuthForm";

export default function SignupPage() {
  return <AuthFrame title="도입사 가입 신청" description="도입사와 담당자 정보를 등록하세요. 운영자 승인 후 로그인할 수 있습니다."><AuthForm mode="signup" /></AuthFrame>;
}
