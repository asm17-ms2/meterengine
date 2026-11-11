import Link from "next/link";
import styles from "./LegalDocument.module.css";
import { BrandMark } from "@/components/shell/BrandMark";
import { CONSENT_DOCUMENTS, consentEffectiveDate } from "@/lib/auth/consents";

export type LegalSection = { title: string; body: React.ReactNode };

export function LegalDocument({ href, intro, sections, numbering = "section" }: { href: (typeof CONSENT_DOCUMENTS)[number]["href"]; intro?: React.ReactNode; sections: LegalSection[]; numbering?: "section" | "article" }) {
  const current = CONSENT_DOCUMENTS.find(doc => doc.href === href)!;
  const heading = (index: number, title: string) => numbering === "article" ? `제${index + 1}조 (${title})` : `${index + 1}. ${title}`;
  return <div className={styles.page}>
    <header className={styles.header}>
      <Link href="/" className={styles.brand} aria-label="MeterEngine 소개"><BrandMark />MeterEngine</Link>
      <nav className={styles.navigation} aria-label="약관과 정책">
        {CONSENT_DOCUMENTS.map(doc => <Link key={doc.href} href={doc.href} aria-current={doc.href === href ? "page" : undefined}>{doc.title}</Link>)}
        <Link href="/login">로그인</Link>
      </nav>
    </header>
    <main id="main-content" className={styles.document}>
      <h1>MeterEngine {current.title}</h1>
      <p className={styles.effective}>시행일: {consentEffectiveDate()}</p>
      {intro}
      <nav className={styles.toc} aria-labelledby="legal-toc-title">
        <h2 id="legal-toc-title">목차</h2>
        <ol>{sections.map((section, index) => <li key={section.title}><a href={`#section-${index + 1}`}>{section.title}</a></li>)}</ol>
      </nav>
      {sections.map((section, index) => <section key={section.title} id={`section-${index + 1}`} aria-labelledby={`section-${index + 1}-title`}>
        <h2 id={`section-${index + 1}-title`}>{heading(index, section.title)}</h2>
        {section.body}
      </section>)}
    </main>
  </div>;
}

export function LegalTable({ label, head, rows, rowHeader = false }: { label: string; head?: string[]; rows: React.ReactNode[][]; rowHeader?: boolean }) {
  return <div className={styles.tableScroll} role="region" aria-label={label} tabIndex={0}>
    <table>
      {head && <thead><tr>{head.map(cell => <th key={cell} scope="col">{cell}</th>)}</tr></thead>}
      <tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => rowHeader && cellIndex === 0 ? <th key={cellIndex} scope="row">{cell}</th> : <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody>
    </table>
  </div>;
}
