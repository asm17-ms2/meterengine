import Link from "next/link";

const STEPS: Record<"signup" | "join" | "operator" | "login", [string, string][]> = {
  login: [["로그인", "가입한 이메일이나 받은 계정으로 로그인"], ["약관 동의", "처음 로그인할 때 필요하면 확인"], ["업무 시작", "고객, 사용량, 청구 확인"]],
  operator: [["계정 확인", "플랫폼 운영자가 생성한 계정으로 로그인"], ["약관 동의", "이용약관과 개인정보 처리방침 확인"], ["플랫폼 운영 시작", "조직 승인과 플랫폼 권한 관리"]],
  join: [["계정 확인", "초대받은 계정이나 지금 로그인한 계정 확인"], ["약관 동의", "이용약관과 개인정보 처리방침 확인"], ["소속 조직으로 이동", "받은 권한으로 업무 시작"]],
  signup: [["가입 신청", "조직과 담당자 정보 입력"], ["가입 승인", "승인 후 로그인"], ["연동 시작", "고객과 요금을 등록하고 사용량 보내기"]],
};

export function AuthFrame({ title, description, children, variant = "signup", currentStep }: { title: string; description: string; children: React.ReactNode; variant?: "signup" | "join" | "operator" | "login"; currentStep?: 1 | 2 | 3 }) {
  const isJoin = variant === "join";
  const isOperator = variant === "operator";
  const isLogin = variant === "login";
  const steps = STEPS[variant];
  return <div className="auth-layout">
    <aside className="auth-context" aria-label={isLogin ? "로그인 안내" : isOperator ? "플랫폼 운영자 계정 확인" : isJoin ? "참여 절차" : "가입 절차"}>
      <Link className="wordmark" href="/">MeterEngine</Link>
      <div className="auth-intro"><p className="auth-intro-title">{isLogin ? "로그인하고 고객별 사용량과 청구를 확인하세요." : isOperator ? "플랫폼 운영을 함께 맡아요." : isJoin ? "팀과 같은 콘솔에서 일을 시작하세요." : "고객이 쓴 만큼 청구하고 결제까지 받으세요."}</p>{!isLogin && <p>{isOperator ? "계정을 확인하고 약관에 동의한 뒤 MeterEngine 운영을 시작하세요." : isJoin ? "계정을 확인하고 약관에 동의하면 소속 조직의 콘솔로 이동해요." : "사용량을 보내면 고객별 요금 계산부터 청구와 결제까지 이어져요."}</p>}</div>
      <ol className="onboarding-steps">{steps.map(([stepTitle, stepDetail], index) => <li key={stepTitle} aria-current={index + 1 === currentStep ? "step" : undefined} className={currentStep ? index + 1 < currentStep ? "is-done" : index + 1 === currentStep ? "is-current" : undefined : undefined}><strong>{stepTitle}</strong><span>{stepDetail}</span></li>)}</ol>
    </aside>
    <main id="main-content" className="auth-main"><div className="auth-panel"><header><h1>{title}</h1><p className="muted">{description}</p></header>{children}</div></main>
  </div>;
}
