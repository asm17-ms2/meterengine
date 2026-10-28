import Link from "next/link";

export function AuthFrame({ title, description, children, variant = "signup" }: { title: string; description: string; children: React.ReactNode; variant?: "signup" | "join" | "operator" | "login" }) {
  const isJoin = variant === "join";
  const isOperator = variant === "operator";
  const isLogin = variant === "login";
  return <div className="auth-layout">
    <aside className="auth-context" aria-label={isLogin ? "로그인 안내" : isOperator ? "플랫폼 운영자 계정 확인" : isJoin ? "도입사 연결 절차" : "가입 절차"}>
      <Link className="wordmark" href="/">MeterEngine</Link>
      <div className="auth-intro"><p className="auth-intro-title">{isLogin ? <>계정으로 로그인해<br />담당 업무를 시작하세요.</> : isOperator ? <>플랫폼 운영을<br />함께 맡습니다.</> : isJoin ? <>함께 관리할<br />도입사로 연결합니다.</> : <>사용량에서<br />청구까지, 한 흐름으로.</>}</p><p>{isLogin ? "계정에 부여된 역할에 따라 도입사 업무 또는 플랫폼 운영 화면으로 이동합니다." : isOperator ? "계정과 이용 안내를 확인한 뒤 MeterEngine 운영을 시작하세요." : isJoin ? "계정과 안내를 확인한 뒤 소속 도입사의 업무를 시작하세요." : <>서비스의 사용량과 요금을 연결하고<br />고객별 청구 근거를 확인하세요.</>}</p></div>
      {isLogin ? <ol className="onboarding-steps"><li><strong>계정 로그인</strong><span>가입한 이메일 또는 전달받은 계정으로 로그인</span></li><li><strong>이용 안내 확인</strong><span>첫 로그인에 안내 확인이 필요한 계정은 본인 동의</span></li><li><strong>담당 업무 시작</strong><span>도입사 업무 또는 MeterEngine 플랫폼 운영</span></li></ol> : isOperator ? <ol className="onboarding-steps"><li><strong>계정 확인</strong><span>플랫폼 운영자가 생성한 계정으로 로그인</span></li><li><strong>안내 동의</strong><span>이용 안내와 개인정보 처리 내용 확인</span></li><li><strong>플랫폼 운영 시작</strong><span>도입사 승인과 플랫폼 권한 관리</span></li></ol> : isJoin ? <ol className="onboarding-steps"><li><strong>계정 확인</strong><span>초대받은 계정 또는 현재 로그인 계정 확인</span></li><li><strong>안내 동의</strong><span>이용 안내와 개인정보 처리 내용 확인</span></li><li><strong>소속 도입사로 이동</strong><span>부여된 권한으로 도입사 업무 시작</span></li></ol> : <ol className="onboarding-steps"><li><strong>가입 신청</strong><span>도입사와 담당자 정보 등록</span></li><li><strong>운영자 승인</strong><span>신청 확인 후 도입사 활성화</span></li><li><strong>로그인과 연동</strong><span>고객, 미터와 사용량 운영 시작</span></li></ol>}
    </aside>
    <main id="main-content" className="auth-main"><div className="auth-panel"><header><h1>{title}</h1><p className="muted">{description}</p></header>{children}</div></main>
  </div>;
}
