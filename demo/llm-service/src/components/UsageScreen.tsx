import { METRICS, MODELS, SURFACES } from "@/lib/catalog";
import type { SurfaceId } from "@/lib/catalog";
import type { TokenUsage, WorkspaceSnapshot } from "@/lib/contracts";
import { Notice, SyncStatus } from "./Feedback";
import { formatDate, formatPrice, formatTokens, METRIC_KEYS, modelName, surfaceName } from "./format";
import { TokenMeter } from "./TokenMeter";

type Row = { modelId: string; surface: SurfaceId; usage: TokenUsage };

function usageRows(snapshot: WorkspaceSnapshot): Row[] {
  const rows = new Map<string, Row>();
  for (const metric of snapshot.usage.metrics) for (const part of metric.breakdown) {
    const key = `${part.modelId}\n${part.surface}`;
    const row = rows.get(key) ?? { modelId: part.modelId, surface: part.surface, usage: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 } };
    row.usage[metric.key] += part.rawQuantity;
    rows.set(key, row);
  }
  const modelOrder = (id: string) => { const index = MODELS.findIndex(model => model.id === id); return index < 0 ? MODELS.length : index; };
  const surfaceOrder = (id: SurfaceId) => SURFACES.findIndex(surface => surface.id === id);
  return [...rows.values()].sort((left, right) => modelOrder(left.modelId) - modelOrder(right.modelId) || surfaceOrder(left.surface) - surfaceOrder(right.surface));
}

export function UsageScreen({ snapshot, isRetrying, onRetryMetering }: { snapshot: WorkspaceSnapshot; isRetrying: boolean; onRetryMetering: () => void }) {
  const totals = Object.fromEntries(METRIC_KEYS.map(key => [key, snapshot.usage.metrics.find(metric => metric.key === key)?.rawQuantity ?? 0])) as TokenUsage;
  const rows = usageRows(snapshot);
  return <>
    {snapshot.usage.state === "unavailable" && <Notice title="사용량을 불러오지 못했어요">{snapshot.usage.error || "잠시 뒤 다시 불러와 주세요. 확인하지 못한 사용량을 0으로 보여 주지 않아요."}</Notice>}
    <section className="panel usage-ledger" aria-labelledby="usage-ledger-title">
      <div className="section-title"><h2 id="usage-ledger-title">{snapshot.month} 토큰 사용량</h2><span className="field-help">{formatDate(snapshot.usage.updatedAt)} 기준</span></div>
      {snapshot.usage.state === "ready" ? <TokenMeter usage={totals} /> : <p className="empty-state">사용량을 확인하면 여기에 보여 드려요.</p>}
      <p className="field-help">MeterEngine에 기록된 사용량이에요. 요금은 100만 토큰 단위로 계산해요.</p>
    </section>
    <SyncStatus snapshot={snapshot} isBusy={isRetrying} onRetry={onRetryMetering} />
    {snapshot.usage.state === "ready" && <section className="data-section" aria-labelledby="usage-breakdown-title">
      <div className="section-title"><h2 id="usage-breakdown-title">모델과 용도별 사용량</h2><span className="field-help">단위: 토큰</span></div>
      {rows.length === 0 ? <p className="empty-state">이달에는 아직 사용량이 없어요. 대화, API, 일괄 처리로 요청을 보내면 여기에 쌓여요.</p>
        : <div className="table-scroll"><table className="usage-table">
          <thead><tr><th scope="col">모델</th><th scope="col">용도</th>{METRICS.map(metric => <th scope="col" key={metric.key}>{metric.name}</th>)}</tr></thead>
          <tbody>{rows.map(row => <tr key={`${row.modelId}-${row.surface}`}><th scope="row">{modelName(row.modelId)}</th><td className="cell-text">{surfaceName(row.surface)}</td>{METRIC_KEYS.map(key => <td key={key}>{formatTokens(row.usage[key])}</td>)}</tr>)}</tbody>
        </table></div>}
    </section>}
    <section className="data-section" aria-labelledby="price-title">
      <h2 id="price-title">적용 단가</h2>
      <p className="section-description">100만 토큰당 원화 단가예요. 부가세는 청구할 때 따로 더해요.</p>
      <div className="pricing-grid">{snapshot.models.map(model => <article key={model.id} className="panel">
        <h3>{model.name}</h3><p className="field-help">{model.description}</p>
        <div className="table-scroll"><table className="price-table">
          <caption className="screen-reader-only">{model.name}의 용도별 단가</caption>
          <thead><tr><th scope="col">토큰</th>{model.surfaces.map(surface => <th scope="col" key={surface}>{surfaceName(surface)}</th>)}</tr></thead>
          <tbody>{METRICS.map(metric => <tr key={metric.key}><th scope="row">{metric.name}</th>{model.surfaces.map(surface => <td key={surface}>{model.kind === "embedding" && metric.key !== "input" ? "해당 없음" : formatPrice(model.prices[surface]?.[metric.key])}</td>)}</tr>)}</tbody>
        </table></div>
      </article>)}</div>
      <p className="provider-note">{snapshot.provider.tokenMethod}</p>
    </section>
  </>;
}
