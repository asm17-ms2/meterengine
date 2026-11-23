import styles from "./insights.module.css";
import { barRatio, edgeSide } from "@/lib/insights";

export type ChartSeries = { name: string; color: string };
// label을 주면 그 기둥 위에 늘 보이는 글자로 적는다. isPending은 값이 아직 늘 수 있는 기둥이다.
export type ChartColumn = { key: string; axisLabel: string; name: string; values: number[]; label?: string; isPending?: boolean };

// 값을 기둥 아래 표로도 적을지는 부르는 쪽이 정한다. 기둥이 많으면 표가 겹쳐 읽을 수 없다.
const PENDING_SUFFIX = ", 집계 중";

export function ColumnChart({ title, summary, series, columns, formatValue, formatCompact }: { title: string; summary: string; series: ChartSeries[]; columns: ChartColumn[]; formatValue: (value: number) => string; formatCompact?: (value: number) => string }) {
  const max = Math.max(0, ...columns.flatMap(column => column.values));
  const describe = (column: ChartColumn) => `${column.name}: ${series.map((item, index) => `${item.name} ${formatValue(column.values[index])}`).join(", ")}${column.isPending ? PENDING_SUFFIX : ""}`;
  return <figure className={styles.chart}>
    <h3>{title}</h3>
    <p>{summary}</p>
    {series.length > 1 && <ul className={styles.legend} aria-hidden="true">{series.map(item => <li key={item.name}><span className={styles.swatch} style={{ "--series": item.color } as React.CSSProperties} />{item.name}</li>)}</ul>}
    {/* 기둥에 마우스를 올리거나 키보드로 옮기면 값이 말풍선으로 뜬다. 브라우저 기본 title은 늦게 뜨고 터치와 키보드에서 안 나온다. */}
    <ol className={styles.plot}>{columns.map((column, index) => <li key={column.key} className={styles.group} tabIndex={0} data-side={edgeSide(index, columns.length)}>
      <span className={styles.tip}>{describe(column)}</span>
      {column.label && <span aria-hidden="true" className={styles.columnLabel} style={{ bottom: `${barRatio(Math.max(...column.values), max) * 100}%` }}>{column.label}</span>}
      {column.values.map((value, index) => <span key={series[index].name} aria-hidden="true" className={column.isPending ? `${styles.column} ${styles.pending}` : styles.column} style={{ "--series": series[index].color, height: `${barRatio(value, max) * 100}%` } as React.CSSProperties} />)}
    </li>)}</ol>
    <ol className={styles.axis} aria-hidden="true">{columns.map(column => <li key={column.key}>{column.axisLabel}</li>)}</ol>
    {formatCompact && series.map((item, index) => <ol key={item.name} className={styles.values} aria-hidden="true">{columns.map(column => <li key={column.key} title={`${item.name} ${formatValue(column.values[index])}`}>{series.length > 1 && <span className={styles.swatch} style={{ "--series": item.color } as React.CSSProperties} />}{formatCompact(column.values[index])}</li>)}</ol>)}
  </figure>;
}
