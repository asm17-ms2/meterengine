import type { ReactNode } from "react";
import styles from "./registration.module.css";

const STEPS = ["자동결제 동의", "토스 카드 인증", "등록 결과 확인"];

export function RegistrationFrame({ title, organizationName, currentStep, children }: { title: string; organizationName?: string; currentStep?: 1 | 3; children: ReactNode }) {
  return <div className={styles.page}>
    <header className={styles.brand}><span>MeterEngine</span><span>결제 카드 등록</span></header>
    <main id="main-content" className={styles.main}>
      <aside className={styles.context}>
        <p className={styles.eyebrow}>{organizationName ?? "사용량 기반 서비스"}</p>
        <p className={styles.headline}>서비스 이용료를<br /> 결제할 카드입니다.</p>
        <p>카드 정보는 토스페이먼츠에서 입력하고 인증합니다.</p>
        <ol className={styles.steps} aria-label="카드 등록 절차">{STEPS.map((step, index) => <li key={step} aria-current={index + 1 === currentStep ? "step" : undefined}>{step}</li>)}</ol>
      </aside>
      <section className={styles.panel} aria-labelledby="registration-title">
        <h1 id="registration-title">{title}</h1>
        {children}
      </section>
    </main>
    <footer className={styles.footer}>카드 등록과 결제 관련 문의는 서비스를 제공하는 담당자에게 문의해 주세요.</footer>
  </div>;
}
