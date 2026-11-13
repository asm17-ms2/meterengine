import type { TokenUsage } from "@/lib/contracts";
import { formatTokens, METRIC_KEYS, metricShortName } from "./format";

// 요청 하나의 토큰 넷을 한 줄 막대와 숫자로 보여 준다. 막대는 장식이고 숫자가 내용이다.
export function TokenMeter({ usage, label, isCompact = false }: { usage: TokenUsage; label?: string; isCompact?: boolean }) {
  const total = METRIC_KEYS.reduce((sum, key) => sum + usage[key], 0);
  return <div className={`token-meter${isCompact ? " token-meter-compact" : ""}`}>
    {label && <p className="token-meter-label">{label}</p>}
    <div className="token-meter-bar" aria-hidden="true">{METRIC_KEYS.map(key => usage[key] > 0 && <span key={key} className={`token-${key}`} style={{ flexGrow: usage[key] }} />)}{total === 0 && <span className="token-empty" />}</div>
    <dl className="token-meter-values">{METRIC_KEYS.map(key => <div key={key}><dt><span className={`token-swatch token-${key}`} aria-hidden="true" />{metricShortName(key)}</dt><dd>{formatTokens(usage[key])}</dd></div>)}</dl>
  </div>;
}
