import styles from "./insights.module.css";
import { barRatio, type RankedAmount } from "@/lib/insights";

export function RankBars({ title, summary, rows, formatValue, empty }: { title: string; summary: string; rows: RankedAmount[]; formatValue: (value: number) => string; empty: string }) {
  const max = Math.max(0, ...rows.map(row => row.amount));
  return <figure className={styles.chart}>
    <h3>{title}</h3>
    <p>{rows.length ? summary : empty}</p>
    <ol className={styles.rank}>{rows.map(row => <li key={row.label}>
      <span className={styles.rankLabel} title={row.label}>{row.label}</span>
      <span className={styles.rankValue}>{formatValue(row.amount)}</span>
      <span className={styles.rankTrack} aria-hidden="true"><span className={styles.rankBar} style={{ display: "block", width: `${barRatio(row.amount, max) * 100}%` }} /></span>
    </li>)}</ol>
  </figure>;
}
