import Link from "next/link";
import { AuthFrame } from "@/components/auth/AuthFrame";

export default function PendingPage() {
  return <AuthFrame title="가입 신청 후 승인 안내" description="가입 신청을 마쳤다면 운영자의 승인을 기다려 주세요."><div className="pending-note"><h2>승인 후 직접 로그인하세요</h2><p>신청한 이메일과 비밀번호로 로그인할 수 있습니다. 승인 전에는 도입사 업무 화면에 접근할 수 없습니다.</p><p>이 화면은 실시간 승인 상태를 표시하지 않습니다. 승인 여부는 운영자에게 확인하세요.</p></div><Link className="primary-link" href="/login">로그인 화면으로</Link></AuthFrame>;
}
