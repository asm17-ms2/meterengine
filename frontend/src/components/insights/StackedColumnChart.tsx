import styles from "./insights.module.css";
import { barRatio, edgeSide } from "@/lib/insights";

// isHatched는 아직 정해지지 않은 값의 조각이다. 빗금으로 그린다.
export type StackSegment = { name: string; color: string; isHatched?: boolean };
export type StackColumn = { key: string; axisLabel: string; name: string; values: number[]; label: string };

// 기둥 하나에 조각을 아래부터 쌓는다. 기둥 높이는 조각의 합이다.
export function StackedColumnChart({ title, summary, segments, columns, formatValue }: { title: string; summary: string; segments: StackSegment[]; columns: StackColumn[]; formatValue: (value: number) => string }) {
  const totalOf = (column: StackColumn) => column.values.reduce((sum, value) => sum + value, 0);
  const max = Math.max(0, ...columns.map(totalOf));
  const describe = (column: StackColumn) => `${column.name}: ${segments.map((segment, index) => `${segment.name} ${formatValue(column.values[index])}`).join(", ")}`;
  const segmentClass = (segment: StackSegment) => segment.isHatched ? `${styles.segment} ${styles.pending}` : styles.segment;
  return <figure className={styles.chart}>
    <h3>{title}</h3>
    <p>{summary}</p>
    <ul className={styles.legend} aria-hidden="true">{segments.map(segment => <li key={segment.name}><span className={segment.isHatched ? `${styles.swatch} ${styles.pending}` : styles.swatch} style={{ "--series": segment.color } as React.CSSProperties} />{segment.name}</li>)}</ul>
    <ol className={styles.plot}>{columns.map((column, index) => <li key={column.key} className={styles.group} tabIndex={0} data-side={edgeSide(index, columns.length)}>
      <span className={styles.tip}>{describe(column)}</span>
      <span aria-hidden="true" className={styles.columnLabel} style={{ bottom: `${barRatio(totalOf(column), max) * 100}%` }}>{column.label}</span>
      <span aria-hidden="true" className={styles.stack} style={{ height: `${barRatio(totalOf(column), max) * 100}%` }}>{segments.map((segment, segmentIndex) => <span key={segment.name} className={segmentClass(segment)} style={{ "--series": segment.color, flexGrow: column.values[segmentIndex] } as React.CSSProperties} />)}</span>
    </li>)}</ol>
    <ol className={styles.axis} aria-hidden="true">{columns.map(column => <li key={column.key}>{column.axisLabel}</li>)}</ol>
  </figure>;
}
