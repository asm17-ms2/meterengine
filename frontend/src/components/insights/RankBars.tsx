import styles from "./insights.module.css";
import { barRatio, type RankedAmount } from "@/lib/insights";

const NO_SHARE = () => "";

// formatShare를 주면 값 옆에 전체에서 차지하는 몫을 적는다.
export function RankBars({ title, summary, rows, formatValue, formatShare = NO_SHARE, empty }: { title: string; summary: string; rows: RankedAmount[]; formatValue: (value: number) => string; formatShare?: (value: number) => string; empty: string }) {
  const max = Math.max(0, ...rows.map(row => row.amount));
  return <figure className={styles.chart}>
    <h3>{title}</h3>
    <p>{rows.length ? summary : empty}</p>
    <ol className={styles.rank}>{rows.map(row => <li key={row.label}>
      <span className={styles.rankLabel} title={row.label}>{row.label}</span>
      <span className={styles.rankValue}>{formatValue(row.amount)}{formatShare(row.amount) && <span className={styles.rankShare}><span className={styles.hidden}>전체의 </span>{formatShare(row.amount)}</span>}</span>
      <span className={styles.rankTrack} aria-hidden="true"><span className={styles.rankBar} style={{ display: "block", width: `${barRatio(row.amount, max) * 100}%` }} /></span>
    </li>)}</ol>
  </figure>;
}
