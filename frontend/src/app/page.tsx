import type { Metadata } from "next";
import Link from "next/link";
import styles from "./HomePage.module.css";

export const metadata: Metadata = {
  title: "MeterEngine - 사용량과 청구의 근거를 연결하세요",
  description: "고객별 사용량, 적용 요금과 청구 근거를 확인하고 확정 인보이스와 결제 결과를 관리하는 사용량 기반 과금 플랫폼",
};

const FLOW = [
  { title: "사용량을 고객과 연결", body: "외부 고객 ID로 고객을 연결하고 서비스에서 발생한 이벤트를 보냅니다. 발생 시각과 수신 시각을 구분하고 원본 속성을 확인합니다.", result: "고객별 원본 이벤트와 집계" },
  { title: "미터와 적용 요금 설정", body: "사용량을 합계, 건수, 평균 등으로 집계하고, 적용 월과 고객에 맞는 단가를 연결합니다. 과금 단위와 반올림, 구간 단가, 기본료와 최소 사용료도 설정할 수 있습니다.", result: "기간과 고객에 맞는 계산 기준" },
  { title: "청구 근거를 먼저 검토", body: "집계 수량과 계산 금액을 대조합니다. 단가 없는 조합과 정정 수량을 확인한 뒤 청구할 내용을 결정합니다.", result: "수량, 단가와 금액의 연결" },
  { title: "확정과 결제 결과 관리", body: "인보이스를 확정하고 등록 카드로 결제를 요청합니다. 결제 시도, 결과 확인과 취소를 각각의 기록으로 확인합니다.", result: "확정 내역과 수납, 취소 이력" },
];

export default function Home() {
  return <div className={styles.page}>
    <header className={styles.header}>
      <Link href="/" className={styles.brand} aria-label="MeterEngine 소개"><span className={styles.mark} aria-hidden="true"><i /><i /><i /></span>MeterEngine</Link>
      <nav className={styles.navigation} aria-label="서비스 소개"><a href="#flow">운영 흐름</a><a href="#roles">역할과 연동</a><Link href="/login">로그인</Link><Link className={styles.navAction} href="/signup">가입 신청</Link></nav>
    </header>
    <main id="main-content">
      <section className={styles.hero} aria-labelledby="home-title">
        <div className={styles.heroCopy}>
          <h1 id="home-title">사용량과 청구의<br /><span>근거를 연결하세요.</span></h1>
          <p>서비스의 사용량을 고객과 연결하고, 적용 요금과 계산 내역을 확인한 뒤 청구와 결제 결과를 관리하세요.</p>
          <div className={styles.actions}><Link href="/signup" className={styles.primaryAction}>도입사 가입 신청</Link><Link href="/login" className={styles.secondaryAction}>기존 계정 로그인</Link></div>
          <p className={styles.applicationNote}>가입 신청 후 운영자 승인을 거쳐 도입사 업무를 시작합니다.</p>
        </div>
        <div className={styles.trace} aria-label="같은 고객과 청구 월로 이어지는 확인 항목">
          <div className={styles.traceHeader}><strong>한 고객, 한 청구 월</strong><span>무엇을 확인하나요</span></div>
          <dl>
            <div><dt>사용량</dt><dd>고객과 연결된 이벤트와 집계 수량</dd></div>
            <div><dt>요금</dt><dd>그 달에 적용되는 미터별 단가와 기본료</dd></div>
            <div><dt>청구 근거</dt><dd>수량에서 금액까지, 계산된 각 항목</dd></div>
            <div><dt>결제 결과</dt><dd>확정 청구액과 수납, 취소 내역</dd></div>
          </dl>
          <p>확정한 계산 내역을 남기고,<br />이후의 결제와 취소는 별도로 기록합니다.</p>
        </div>
      </section>
      <section id="flow" className={styles.flowSection} aria-labelledby="flow-title">
        <div className={styles.sectionHeading}><h2 id="flow-title">기록한 사용량이<br />어떻게 청구됐는지 확인하세요.</h2><p>수집과 계산, 청구 확정과 결제를 구분합니다. 각 단계의 결과를 확인하며 다음 업무로 이어 가세요.</p></div>
        <ol className={styles.flow}>{FLOW.map((step, index) => <li key={step.title}><span className={styles.stepNumber} aria-hidden="true">{index + 1}</span><h3>{step.title}</h3><p>{step.body}</p><strong>{step.result}</strong></li>)}</ol>
      </section>
      <section className={styles.evidenceSection} aria-labelledby="evidence-title">
        <div><h2 id="evidence-title">금액 옆에,<br />판단할 근거를 함께.</h2><p>청구 예정액과 확정 금액은 같은 상태가 아닙니다. 사용량을 검토하는 순간부터 수납을 확인할 때까지 필요한 정보를 구별해 보여 줍니다.</p></div>
        <ul className={styles.evidenceList}>
          <li><h3>어떤 사용량이 빠졌는지</h3><p>미터와 연결되지 않은 유형, 집계할 수 없는 속성과 미래 시각의 이벤트를 확인합니다. 단가 없는 조합도 청구 검토에서 따로 살펴봅니다.</p></li>
          <li><h3>그때 어떤 요금이 적용됐는지</h3><p>적용 월과 고객별 가격을 확인합니다. 확정된 인보이스는 당시 수량과 단가, 금액을 보존합니다.</p></li>
          <li><h3>결제와 취소가 어떻게 진행됐는지</h3><p>결제 완료, 실패와 결과 확인 중을 구분합니다. 취소한 금액은 확정 청구액을 바꾸지 않고 별도 내역으로 남습니다.</p></li>
        </ul>
      </section>
      <section id="roles" className={styles.rolesSection} aria-labelledby="roles-title">
        <div className={styles.sectionHeading}><h2 id="roles-title">업무 역할과 서버 권한을<br />구분해 연결합니다.</h2><p>도입사의 업무 화면, 연동 서버의 API 키와 고객의 카드 등록은 각각의 목적에 맞는 경로로 이어집니다.</p></div>
        <dl className={styles.roles}>
          <div><dt>도입사 관리자</dt><dd>고객과 요금을 관리하고 청구 근거와 수납을 확인합니다. 대표관리자는 도입사 안에서 함께 일할 계정을 관리합니다.</dd></div>
          <div><dt>도입사 연동 서버</dt><dd>API 키의 조회(read), 수집(ingest), 전체 관리(admin) 권한으로 연동합니다. 고객 화면에 필요한 청구 정보를 연결하고 웹훅으로 처리 결과를 받습니다.</dd></div>
          <div><dt>도입사 고객</dt><dd>전달받은 카드 등록 링크에서 자동결제 동의를 확인하고 토스페이먼츠 카드 인증을 진행합니다. 카드 정보는 인증 화면에서 입력합니다.</dd></div>
          <div><dt>MeterEngine 운영자</dt><dd>도입사 가입 승인과 플랫폼 운영 권한을 관리합니다. 도입사 내부의 관리자 권한과 구분됩니다.</dd></div>
        </dl>
        <p className={styles.modeNote}>테스트 업무 환경과 실제 업무 환경을 구분합니다. 현재 환경별 고객, 수집 로그와 연동 키를 지원하며 청구와 결제의 환경 분리는 준비 중입니다. 기존 데이터, PG 결제 환경과 화면 시연 모드는 각각 구분해 확인하세요.</p>
      </section>
      <section className={styles.startSection} aria-labelledby="start-title"><div><h2 id="start-title">첫 청구의 근거부터 준비하세요.</h2><p>도입사 가입을 신청하고 고객, 미터와 적용 요금을 연결하세요.</p></div><Link className={styles.primaryAction} href="/signup">도입사 가입 신청</Link></section>
    </main>
    <footer className={styles.footer}><Link href="/" className={styles.footerBrand}>MeterEngine</Link><span>사용량 기반 과금 운영</span><div><Link href="/login">로그인</Link><Link href="/signup">가입 신청</Link></div></footer>
  </div>;
}
