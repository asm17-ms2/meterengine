"use client";

import { ThunderboltOutlined } from "@ant-design/icons";
import { Button, Input } from "antd";
import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { defaultModelId, METRICS, MODELS } from "@/lib/catalog";
import { BATCH_ITEM_MAX_LENGTH, BATCH_MAX_ITEMS } from "@/lib/contracts";
import type { BatchView, GenerationView, TokenUsage, WorkspaceSnapshot } from "@/lib/contracts";
import { createRequestId } from "@/lib/request-id";
import { ApiFailure, errorMessage, jsonRequest, readApi } from "./client-api";
import { MeteringLabel, Notice } from "./Feedback";
import { formatPrice, formatTokens, METRIC_KEYS } from "./format";
import { TokenMeter } from "./TokenMeter";

type PendingBatch = { batchId: string; modelId: string; items: Array<{ requestId: string; prompt: string }> };

const BATCH_MODELS = MODELS.filter(model => model.surfaces.includes("batch"));
const SAMPLE_ITEMS = ["배송이 사흘째 오지 않아요.", "결제 영수증을 다시 받고 싶어요.", "새로 나온 요금제가 궁금해요.", "앱이 자꾸 로그아웃돼요."].join("\n");

function batchLines(text: string): string[] {
  return text.split("\n").map(line => line.trim()).filter(Boolean);
}

function totalUsage(items: GenerationView[]): TokenUsage {
  const total: TokenUsage = { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 };
  for (const item of items) for (const key of METRIC_KEYS) total[key] += item.usage[key];
  return total;
}

function resultPreview(item: GenerationView): string {
  if (item.state === "failed") return "응답을 만들지 못했어요. 사용량에 넣지 않아요.";
  if (MODELS.find(model => model.id === item.modelId)?.kind === "embedding") return `${formatTokens((JSON.parse(item.output) as number[]).length)}차원 임베딩`;
  return item.output;
}

export function BatchScreen({ snapshot, onUsageChanged, onSessionLost }: { snapshot: WorkspaceSnapshot; onUsageChanged: () => void; onSessionLost: () => void }) {
  const [modelId, setModelId] = useState(defaultModelId("batch"));
  const [text, setText] = useState("");
  const [result, setResult] = useState<BatchView | null>(null);
  const [error, setError] = useState("");
  const [isSending, setIsSending] = useState(false);
  const pending = useRef<PendingBatch | null>(null);
  const model = BATCH_MODELS.find(item => item.id === modelId)!;
  const prices = snapshot.models.find(item => item.id === modelId)?.prices;
  const lines = batchLines(text);
  const rows = METRICS.filter(metric => model.kind === "text" || metric.key === "input");
  const pairs = rows.map(metric => [prices?.api?.[metric.key], prices?.batch?.[metric.key]] as const).filter(([api, batch]) => api != null && batch != null);
  const isHalf = pairs.length > 0 && pairs.every(([api, batch]) => batch! * 2 === api);

  const send = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSending) return;
    if (lines.length === 0 || lines.length > BATCH_MAX_ITEMS || lines.some(line => Array.from(line).length > BATCH_ITEM_MAX_LENGTH)) {
      setError(`작업을 한 줄에 한 건씩 1건부터 ${BATCH_MAX_ITEMS}건까지 넣어 주세요. 건마다 2,000자까지 넣을 수 있어요.`);
      document.getElementById("batch-items")?.focus();
      return;
    }
    const previous = pending.current;
    const isSame = previous && previous.modelId === modelId && previous.items.length === lines.length && previous.items.every((item, index) => item.prompt === lines[index]);
    const batch = isSame ? previous : { batchId: createRequestId(), modelId, items: lines.map(prompt => ({ requestId: createRequestId(), prompt })) };
    pending.current = batch;
    setIsSending(true); setError("");
    try {
      const view = await readApi<BatchView>("/api/batches", jsonRequest(batch));
      setResult(view);
      if (view.items.some(item => item.state !== "completed")) setError("일부 작업의 응답을 만들지 못했어요. 같은 목록으로 다시 시작하면 남은 작업만 이어서 처리해요.");
      else pending.current = null;
      onUsageChanged();
    } catch (problem) {
      setError(errorMessage(problem, "일괄 처리 결과를 확인하지 못했어요. 같은 목록으로 다시 시작하면 중복 없이 이어서 확인해요."));
      if (problem instanceof ApiFailure && problem.status === 401) onSessionLost();
    } finally { setIsSending(false); }
  };

  const completed = result?.items.filter(item => item.state === "completed") ?? [];
  return <div className="batch-layout">
    <form className="panel batch-form" onSubmit={event => void send(event)} aria-busy={isSending}>
      <h2>작업 넣기</h2>
      <div className="field">
        <label htmlFor="batch-model">모델</label>
        <select id="batch-model" value={modelId} onChange={event => { setModelId(event.target.value); setError(""); }} disabled={isSending}>{BATCH_MODELS.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
        <p className="field-help">{model.description}</p>
      </div>
      <div className="field">
        <div className="field-heading"><label htmlFor="batch-items">작업 목록</label><button type="button" className="text-button" onClick={() => setText(SAMPLE_ITEMS)} disabled={isSending}>예시 넣기</button></div>
        <Input.TextArea id="batch-items" value={text} onChange={event => { setText(event.target.value); setError(""); }} autoSize={{ minRows: 8, maxRows: 18 }} disabled={isSending}
          placeholder={"한 줄에 한 건씩 적어요.\n예: 고객 문의 한 건, 분류할 문장 하나"} aria-invalid={Boolean(error)} aria-describedby="batch-items-help" />
        <p id="batch-items-help" className={`field-help${lines.length > BATCH_MAX_ITEMS ? " field-help-over" : ""}`}>{formatTokens(lines.length)} / {BATCH_MAX_ITEMS}건. 건마다 요청 하나로 기록해요.</p>
      </div>
      {error && <Notice>{error}</Notice>}
      <Button htmlType="submit" type="primary" loading={isSending} icon={<ThunderboltOutlined aria-hidden="true" />}>{lines.length > 0 ? `${formatTokens(lines.length)}건 처리 시작` : "일괄 처리 시작"}</Button>
    </form>
    <div className="batch-side">
      <section className="panel batch-price" aria-labelledby="batch-price-title">
        {isHalf && <p className="batch-price-badge">단가 50%</p>}
        <h2 id="batch-price-title">{isHalf ? "일괄 처리는 단가가 절반이에요" : "일괄 처리 단가"}</h2>
        <p className="section-description">바로 답할 필요가 없는 작업을 한 번에 맡기는 방식이라 API보다 싸게 계산해요.</p>
        <div className="table-scroll"><table className="price-table">
          <caption className="screen-reader-only">{model.name}의 100만 토큰당 단가 비교</caption>
          <thead><tr><th scope="col">100만 토큰당</th><th scope="col">API</th><th scope="col">일괄 처리</th></tr></thead>
          <tbody>{rows.map(metric => <tr key={metric.key}><th scope="row">{metric.name}</th><td className="price-regular">{formatPrice(prices?.api?.[metric.key])}</td><td><strong>{formatPrice(prices?.batch?.[metric.key])}</strong></td></tr>)}</tbody>
        </table></div>
        <p className="field-help">부가세 별도. 요금표를 불러오지 못하면 &quot;미확인&quot;으로 보여요.</p>
      </section>
    </div>
    <section className="panel batch-results" aria-labelledby="batch-results-title" aria-live="polite">
      <h2 id="batch-results-title">처리 결과</h2>
      {!result ? <p className="empty-state">작업을 넣고 처리를 시작하면 건마다 결과와 사용량이 여기에 나와요.</p> : <>
        <p className="batch-summary">{formatTokens(result.items.length)}건 가운데 {formatTokens(completed.length)}건 완료. 사용량은 건마다 따로 기록했어요.</p>
        <TokenMeter usage={totalUsage(completed)} label="이번 일괄 처리의 사용량 합계" />
        <ol className="batch-list">{result.items.map((item, index) => <li key={item.id}>
          <span className="batch-index" aria-hidden="true">{index + 1}</span>
          <div className="batch-item">
            <p className="batch-item-prompt">{item.prompt}</p>
            <p className="batch-item-output">{resultPreview(item)}</p>
            {item.state === "completed" && <p className="message-meta"><span>입력 {formatTokens(item.usage.input)}, 출력 {formatTokens(item.usage.output)} 토큰</span><MeteringLabel value={item.meteringStatus} /></p>}
          </div>
        </li>)}</ol>
      </>}
    </section>
  </div>;
}
