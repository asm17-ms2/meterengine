import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import styles from "./HomePage.module.css";
import { BrandMark } from "@/components/shell/BrandMark";
import { DOCS_URL } from "@/lib/docs";

// 소개 화면 워드마크(24px)가 콘솔 헤더(21px)보다 큰 만큼 마크도 키운다.
const HOME_MARK_SCALE = 24 / 21;

export const metadata: Metadata = {
  title: { absolute: "MeterEngine - 사용량 기반 과금과 카드 결제" },
  description: "사용량을 API로 보내면 고객마다 요금을 계산하고, 청구서 발행과 토스페이먼츠 카드 결제까지 이어 주는 사용량 기반 과금 서비스예요.",
};

const FLOW = [
  { title: "요금 정하기", body: "무엇에 얼마를 받을지 정해요." },
  { title: "사용량 보내기", body: "고객이 쓴 만큼 API로 보내요." },
  { title: "금액 확인하기", body: "고객별 금액을 청구 전에 미리 봐요." },
  { title: "청구하고 결제받기", body: "청구서를 보내고 등록된 카드로 받아요." },
];

const EVIDENCE = [
  { title: "빠진 사용량을 청구 전에 찾아요", body: "금액에 들어가지 못한 사용 기록을 따로 모아 보여 줘요." },
  { title: "발행한 청구서는 바뀌지 않아요", body: "요금표를 바꿔도 그때의 수량, 단가, 금액이 그대로 남아요." },
  { title: "결제 기록도 모두 남아요", body: "완료, 실패, 취소를 나눠 기록해 언제든 확인할 수 있어요." },
];

// 로컬 테스트 환경의 실제 화면을 찍은 것이다. 화면을 바꾸면 다시 찍는다.
const SCREENS = [
  { src: "/home/invoice-view.png", width: 944, height: 982, alt: "고객이 링크로 여는 청구서 화면. 항목별 수량, 단가, 공급가액과 부가세, 청구 금액이 보여요.", caption: "고객이 링크로 여는 청구서" },
  { src: "/home/console-insights.png", width: 896, height: 763, alt: "콘솔 홈의 인사이트 화면. 월별 청구액과 받은 금액, 날짜별 이벤트 수, 고객별과 미터별 청구 예정액 그래프가 보여요.", caption: "콘솔 홈의 인사이트" },
];

const ROLES = [
  { name: "고객", body: "링크로 카드를 등록하고 청구서와 결제 상태를 확인해요." },
  { name: "개발팀", body: "API 키와 웹훅으로 연동하고, 키마다 필요한 권한만 줘요." },
  { name: "팀원", body: "대표관리자가 팀원을 초대하고 권한을 정해요." },
  { name: "테스트 환경", body: "실제 데이터와 따로 두고 결제까지 먼저 시험해요. 테스트 키로는 출금되지 않아요." },
];

export default function Home() {
  return <div className={styles.page}>
    <header className={styles.header}>
      <Link href="/" className={styles.brand} aria-label="MeterEngine 소개"><BrandMark scale={HOME_MARK_SCALE} />MeterEngine</Link>
      <nav className={styles.navigation} aria-label="서비스 소개"><a href="#flow">첫 청구까지</a><a href="#integration">고객 화면과 연동</a><a href={DOCS_URL}>문서</a><Link href="/login">로그인</Link><Link className={styles.navAction} href="/signup">가입</Link></nav>
    </header>
    <main id="main-content">
      <section className={styles.hero} aria-labelledby="home-title">
        <div className={styles.heroCopy}>
          <h1 id="home-title">고객이 쓴 만큼 청구하고, <span>카드 결제까지 받으세요.</span></h1>
          <p>사용량을 API로 보내면 고객별 요금을 계산해요. 청구서를 발행하고 고객이 등록한 카드로 결제까지 받으세요.</p>
          <div className={styles.actions}><Link href="/signup" className={styles.primaryAction}>가입하기</Link><Link href="/login" className={styles.secondaryAction}>로그인</Link></div>
          <p className={styles.applicationNote}>이메일을 인증하면 바로 테스트할 수 있어요.</p>
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
        <div className={styles.sectionHeading}><h2 id="flow-title">네 가지만 하면 첫 청구가 끝나요.</h2><p>테스트 환경에서 먼저 끝까지 해 보세요.</p></div>
        <ol className={styles.flow}>{FLOW.map((step, index) => <li key={step.title}><span className={styles.stepNumber} aria-hidden="true">{index + 1}</span><h3>{step.title}</h3><p>{step.body}</p></li>)}</ol>
      </section>
      <section className={styles.evidenceSection} aria-labelledby="evidence-title">
        <div><h2 id="evidence-title">{"\"이 금액이 왜 나왔나요?\""} 근거로 바로 답하세요.</h2><p>청구에 쓴 수량, 단가, 금액을 그대로 보관해요. 근거가 남아 있어 고객이 믿고 결제해요.</p></div>
        <ul className={styles.evidenceList}>{EVIDENCE.map(item => <li key={item.title}><h3>{item.title}</h3><p>{item.body}</p></li>)}</ul>
      </section>
      <section id="integration" className={styles.rolesSection} aria-labelledby="integration-title">
        <div className={styles.sectionHeading}><h2 id="integration-title">고객이 보는 화면도, 내가 보는 화면도 준비돼 있어요.</h2><p>고객은 링크로 청구서를 열고, 나는 콘솔에서 금액 흐름을 봐요.</p></div>
        <div className={styles.screens}>{SCREENS.map(screen => <figure key={screen.src}><Image src={screen.src} alt={screen.alt} width={screen.width} height={screen.height} unoptimized /><figcaption>{screen.caption}</figcaption></figure>)}</div>
        <dl className={styles.roles}>{ROLES.map(role => <div key={role.name}><dt>{role.name}</dt><dd>{role.body}</dd></div>)}</dl>
      </section>
      <section className={styles.startSection} aria-labelledby="start-title"><div><h2 id="start-title">테스트 환경에서 첫 청구서를 발행해 보세요.</h2><p>이메일을 인증하면 바로 시작할 수 있어요.</p></div><Link className={styles.primaryAction} href="/signup">가입하기</Link></section>
    </main>
    <footer className={styles.footer}><Link href="/" className={styles.footerBrand}>MeterEngine</Link><span>사용량 기반 과금 서비스</span><div><Link href="/terms">이용약관</Link><Link href="/privacy" className={styles.footerPolicy}>개인정보 처리방침</Link><a href={DOCS_URL}>문서</a><Link href="/login">로그인</Link><Link href="/signup">가입</Link></div></footer>
  </div>;
}
