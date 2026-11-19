import styles from "./insights.module.css";
import { barRatio } from "@/lib/insights";

export type ChartSeries = { name: string; color: string };
export type ChartColumn = { key: string; axisLabel: string; name: string; values: number[] };

// 값을 기둥 아래 표로도 적을지는 부르는 쪽이 정한다. 기둥이 많으면 표가 겹쳐 읽을 수 없다.
export function ColumnChart({ title, summary, series, columns, formatValue, formatCompact }: { title: string; summary: string; series: ChartSeries[]; columns: ChartColumn[]; formatValue: (value: number) => string; formatCompact?: (value: number) => string }) {
  const max = Math.max(0, ...columns.flatMap(column => column.values));
  const describe = (column: ChartColumn) => `${column.name}: ${series.map((item, index) => `${item.name} ${formatValue(column.values[index])}`).join(", ")}`;
  return <figure className={styles.chart}>
    <h3>{title}</h3>
    <p>{summary}</p>
    {series.length > 1 && <ul className={styles.legend} aria-hidden="true">{series.map(item => <li key={item.name}><span className={styles.swatch} style={{ "--series": item.color } as React.CSSProperties} />{item.name}</li>)}</ul>}
    <ol className={styles.plot}>{columns.map(column => <li key={column.key} className={styles.group} title={describe(column)}>
      <span className={styles.hidden}>{describe(column)}</span>
      {column.values.map((value, index) => <span key={series[index].name} aria-hidden="true" className={styles.column} style={{ "--series": series[index].color, height: `${barRatio(value, max) * 100}%` } as React.CSSProperties} />)}
    </li>)}</ol>
    <ol className={styles.axis} aria-hidden="true">{columns.map(column => <li key={column.key}>{column.axisLabel}</li>)}</ol>
    {formatCompact && series.map((item, index) => <ol key={item.name} className={styles.values} aria-hidden="true">{columns.map(column => <li key={column.key} title={`${item.name} ${formatValue(column.values[index])}`}>{series.length > 1 && <span className={styles.swatch} style={{ "--series": item.color } as React.CSSProperties} />}{formatCompact(column.values[index])}</li>)}</ol>)}
  </figure>;
}
