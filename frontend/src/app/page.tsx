import type { Metadata } from "next";
import Link from "next/link";
import styles from "./HomePage.module.css";
import { BrandMark } from "@/components/shell/BrandMark";

// 소개 화면 워드마크(24px)가 콘솔 헤더(21px)보다 큰 만큼 마크도 키운다.
const HOME_MARK_SCALE = 24 / 21;

export const metadata: Metadata = {
  title: { absolute: "MeterEngine - 사용량 기반 과금과 카드 결제" },
  description: "사용량을 API로 보내면 고객마다 요금을 계산하고, 청구서 발행과 토스페이먼츠 카드 결제까지 이어 주는 사용량 기반 과금 서비스예요.",
};

const FLOW = [
  { title: "요금표 만들기", body: "API 호출 수, 처리 건수처럼 요금을 매길 기준을 정하고 단가를 넣어요. 필요하면 구간 요금, 기본료, 최소 사용료를 더하고, 고객마다 달마다 요금을 다르게 둘 수도 있어요." },
  { title: "고객 등록과 사용량 보내기", body: "서비스에서 쓰던 고객 ID로 고객을 등록하고 사용 기록을 API로 보내요. 여러 건을 한 번에 보내도 돼요." },
  { title: "청구 전에 금액 확인", body: "고객마다 이번 달 예상 금액과 계산 내역을 미리 봐요. 금액에 들어가지 못한 사용량도 이 단계에서 찾아요." },
  { title: "청구서 발행과 결제", body: "금액을 확인한 뒤 청구서를 발행하고, 고객이 등록한 카드로 결제를 요청해요. 결제에는 직접 발급받아 등록한 토스페이먼츠 키를 써요." },
];

export default function Home() {
  return <div className={styles.page}>
    <header className={styles.header}>
      <Link href="/" className={styles.brand} aria-label="MeterEngine 소개"><BrandMark scale={HOME_MARK_SCALE} />MeterEngine</Link>
      <nav className={styles.navigation} aria-label="서비스 소개"><a href="#flow">첫 청구까지</a><a href="#integration">고객 화면과 연동</a><Link href="/login">로그인</Link><Link className={styles.navAction} href="/signup">가입 신청</Link></nav>
    </header>
    <main id="main-content">
      <section className={styles.hero} aria-labelledby="home-title">
        <div className={styles.heroCopy}>
          <h1 id="home-title">고객이 쓴 만큼 청구하고, <span>카드 결제까지 받으세요.</span></h1>
          <p>고객과 사용 기록을 API로 보내면 고객마다 이번 달 요금을 계산해요. 금액을 확인해 청구서를 발행하고, 고객이 등록한 카드로 토스페이먼츠 결제를 받으세요.</p>
          <div className={styles.actions}><Link href="/signup" className={styles.primaryAction}>가입 신청하기</Link><Link href="/login" className={styles.secondaryAction}>로그인</Link></div>
          <p className={styles.applicationNote}>가입이 승인되면 신청한 이메일과 비밀번호로 로그인할 수 있어요.</p>
        </div>
        <div className={styles.trace} role="group" aria-labelledby="home-trace-title">
          <div className={styles.traceHeader}><strong id="home-trace-title">고객이 받는 청구서</strong><span>링크로 전달</span></div>
          <dl>
            <div><dt>항목</dt><dd>과금 기준, 구간, 기본료처럼 금액이 생긴 이유마다 한 줄</dd></div>
            <div><dt>수량과 단가</dt><dd>그 달 사용량과 적용한 단가</dd></div>
            <div><dt>금액</dt><dd>항목별 금액, 부가세와 청구 금액</dd></div>
            <div><dt>결제</dt><dd>결제한 금액, 취소한 금액, 남은 금액</dd></div>
          </dl>
          <p>발행한 청구서의 수량과 금액은 그때 기록 그대로 남아요. 요금표를 바꾸거나 결제를 취소해도 바뀌지 않아요.</p>
        </div>
      </section>
      <section id="flow" className={styles.flowSection} aria-labelledby="flow-title">
        <div className={styles.sectionHeading}><h2 id="flow-title">연동부터 첫 청구까지 네 단계예요.</h2><p>테스트 환경에서 끝까지 해 본 뒤 실제 환경에서 시작하세요. 요금표, API 키, 토스페이먼츠 키는 실제 환경에 새로 등록해요.</p></div>
        <ol className={styles.flow}>{FLOW.map((step, index) => <li key={step.title}><span className={styles.stepNumber} aria-hidden="true">{index + 1}</span><h3>{step.title}</h3><p>{step.body}</p></li>)}</ol>
      </section>
      <section className={styles.evidenceSection} aria-labelledby="evidence-title">
        <div><h2 id="evidence-title">{"\"이 금액이 왜 나왔나요?\""} 고객이 물으면 청구서를 보여 주세요.</h2><p>고객에게 보내는 청구서 링크에는 항목마다 수량, 단가, 금액이 적히고 부가세와 청구 금액이 함께 나와요.</p></div>
        <ul className={styles.evidenceList}>
          <li><h3>청구 전에 빠진 사용량을 찾아요</h3><p>요금을 매길 기준에 맞지 않는 기록, 숫자가 아닌 값, 아직 오지 않은 시각이 적힌 기록을 따로 모아 보여 줘요. 청구서를 발행하기 전에 어떤 사용량이 금액에 들어가지 않았는지 확인하세요.</p></li>
          <li><h3>발행한 청구서는 요금표를 바꿔도 그대로예요</h3><p>발행할 때의 수량, 단가, 금액을 그대로 남겨요. 지난달 청구서를 다시 열어도 같은 금액이 나와요.</p></li>
          <li><h3>결제가 실패하거나 취소돼도 기록이 남아요</h3><p>결제 완료, 실패, 확인 중을 나눠 보여 주고, 결제를 취소해도 청구 금액은 바뀌지 않아요. 결제에 실패한 청구서는 재시도를 허용한 링크에서 고객이 직접 다시 결제할 수 있어요.</p></li>
        </ul>
      </section>
      <section id="integration" className={styles.rolesSection} aria-labelledby="integration-title">
        <div className={styles.sectionHeading}><h2 id="integration-title">고객이 카드를 등록하고 청구서를 보는 화면까지 준비돼 있어요.</h2><p>고객용 화면은 링크로 열고, 개발팀은 API 키와 웹훅으로 연동해요.</p></div>
        <dl className={styles.roles}>
          <div><dt>고객</dt><dd>카드 등록 링크를 받은 고객이 자동결제 동의를 확인하고 토스페이먼츠 인증 화면에서 카드를 등록해요. 카드 정보는 그 인증 화면에만 입력해요. 청구서 링크에서는 금액과 결제 상태를 직접 확인해요.</dd></div>
          <div><dt>개발팀</dt><dd>API 키마다 조회, 고객과 사용량 전송, 전체 관리 가운데 필요한 권한만 골라 발급해요. 결제가 완료되거나 실패하거나 취소되면 웹훅으로 알려 줘요.</dd></div>
          <div><dt>팀원</dt><dd>대표관리자가 팀원을 초대하고 권한을 정해요. 조회만 맡기거나 요금과 청구까지 맡겨요.</dd></div>
          <div><dt>테스트 환경</dt><dd>고객, 사용량, 요금, 청구, 결제 기록을 실제 환경과 따로 써요. 실제 고객 데이터와 섞지 않고 연동과 결제를 먼저 시험해 보세요. 토스페이먼츠 테스트 키로 결제하면 실제로 출금되지 않아요.</dd></div>
        </dl>
      </section>
      <section className={styles.startSection} aria-labelledby="start-title"><div><h2 id="start-title">테스트 환경에서 첫 청구서를 발행해 보세요.</h2><p>요금표를 만들고 테스트 사용량을 보내 보세요. 고객이 받을 청구서를 미리 확인할 수 있어요.</p></div><Link className={styles.primaryAction} href="/signup">가입 신청하기</Link></section>
    </main>
    <footer className={styles.footer}><Link href="/" className={styles.footerBrand}>MeterEngine</Link><span>사용량 기반 과금 서비스</span><div><Link href="/terms">이용약관</Link><Link href="/privacy" className={styles.footerPolicy}>개인정보 처리방침</Link><Link href="/login">로그인</Link><Link href="/signup">가입 신청</Link></div></footer>
  </div>;
}
