"use client";

import styles from "@/components/invoice-links/PublicInvoiceView.module.css";

export default function InvoiceLinkError() {
  return <main id="main-content" className={styles.frame}>
    <header className={styles.brand}><strong>청구서</strong></header>
    <section className={styles.message}>
      <h1>청구서를 불러오지 못했습니다</h1>
      <p>잠시 후 다시 시도해 주세요. 계속 연결되지 않으면 청구서를 보낸 담당자에게 문의해 주세요.</p>
      <button type="button" onClick={() => window.location.reload()}>다시 시도</button>
    </section>
    <p className={styles.poweredBy}>Powered by MeterEngine</p>
  </main>;
}
