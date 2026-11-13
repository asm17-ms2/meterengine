"use client";

import { SendOutlined } from "@ant-design/icons";
import { Button, Input } from "antd";
import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { COMPANY, defaultModelId, MODELS, transactionId } from "@/lib/catalog";
import { PROMPT_MAX_LENGTH, SYSTEM_MAX_LENGTH } from "@/lib/contracts";
import type { GenerationView, WorkspaceSnapshot } from "@/lib/contracts";
import { createRequestId } from "@/lib/request-id";
import { estimateTokens, PROMPT_CACHE_MIN_TOKENS } from "@/lib/tokens";
import { ApiFailure, errorMessage, jsonRequest, readApi, settleGeneration } from "./client-api";
import { MeteringLabel, Notice } from "./Feedback";
import { formatTokens, METRIC_KEYS, priceSummary } from "./format";
import { TokenMeter } from "./TokenMeter";

type PendingCall = { requestId: string; modelId: string; system: string; prompt: string };

const API_MODELS = MODELS.filter(model => model.surfaces.includes("api"));
const API_BASE = `https://api.${COMPANY.emailDomain}/v1`;

function endpoint(isEmbedding: boolean): string {
  return `${API_BASE}/${isEmbedding ? "embeddings" : "responses"}`;
}

function requestPreview(modelId: string, system: string, prompt: string, isEmbedding: boolean): string {
  const body = isEmbedding ? { model: modelId, input: prompt } : { model: modelId, ...(system ? { system } : {}), input: prompt };
  return `POST ${endpoint(isEmbedding)}\nAuthorization: Bearer $${COMPANY.id.toUpperCase()}_API_KEY\nContent-Type: application/json\n\n${JSON.stringify(body, null, 2)}`;
}

function responsePreview(result: GenerationView, isEmbedding: boolean): string {
  const usage = { input_tokens: result.usage.input, output_tokens: result.usage.output, cache_read_tokens: result.usage.cacheRead, cache_write_tokens: result.usage.cacheWrite };
  if (isEmbedding) {
    const vector = JSON.parse(result.output) as number[];
    return JSON.stringify({ id: transactionId(result.id), object: "embedding", model: result.modelId, dimensions: vector.length, embedding: vector, usage, latency_ms: result.latencyMs }, null, 2);
  }
  return JSON.stringify({ id: transactionId(result.id), object: "response", model: result.modelId, output_text: result.output, usage, latency_ms: result.latencyMs }, null, 2);
}

export function ApiPlayground({ snapshot, onUsageChanged, onSessionLost }: { snapshot: WorkspaceSnapshot; onUsageChanged: () => void; onSessionLost: () => void }) {
  const [modelId, setModelId] = useState(defaultModelId("api"));
  const [system, setSystem] = useState("");
  const [prompt, setPrompt] = useState("");
  const [result, setResult] = useState<GenerationView | null>(null);
  const [error, setError] = useState("");
  const [isSending, setIsSending] = useState(false);
  const pending = useRef<PendingCall | null>(null);
  const model = API_MODELS.find(item => item.id === modelId)!;
  const isEmbedding = model.kind === "embedding";
  const prices = snapshot.models.find(item => item.id === modelId)?.prices.api;
  const systemTokens = estimateTokens(system);

  const send = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSending) return;
    const text = prompt.trim();
    const instruction = isEmbedding ? "" : system;
    if (!text || Array.from(text).length > PROMPT_MAX_LENGTH) { setError("메시지를 1자부터 4,000자까지 입력해 주세요."); document.getElementById("api-message")?.focus(); return; }
    if (Array.from(instruction).length > SYSTEM_MAX_LENGTH) { setError("시스템 지시는 4,000자까지 넣을 수 있어요."); document.getElementById("api-system")?.focus(); return; }
    const previous = pending.current;
    const call = previous && previous.modelId === modelId && previous.system === instruction && previous.prompt === text ? previous : { requestId: createRequestId(), modelId, system: instruction, prompt: text };
    pending.current = call;
    setIsSending(true); setError("");
    try {
      const first = await readApi<GenerationView>("/api/generations", jsonRequest({ requestId: call.requestId, surface: "api", modelId, prompt: text, ...(instruction ? { system: instruction } : {}) }));
      const settled = await settleGeneration(first);
      setResult(settled);
      if (settled.state === "failed") setError("응답을 만들지 못했어요. 같은 요청을 다시 보내면 이어서 처리해요.");
      else pending.current = null;
      onUsageChanged();
    } catch (problem) {
      setError(errorMessage(problem, "응답을 받았는지 확인하지 못했어요. 같은 요청을 다시 보내면 중복 없이 이어서 확인해요."));
      if (problem instanceof ApiFailure && problem.status === 401) onSessionLost();
    } finally { setIsSending(false); }
  };

  const isResultEmbedding = result ? MODELS.find(item => item.id === result.modelId)?.kind === "embedding" : false;
  return <div className="api-layout">
    <form className="panel api-form" onSubmit={event => void send(event)} aria-busy={isSending}>
      <h2>요청</h2>
      <div className="field">
        <label htmlFor="api-model">모델</label>
        <select id="api-model" value={modelId} onChange={event => { setModelId(event.target.value); setError(""); }} disabled={isSending}>{API_MODELS.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
        <p className="field-help">{model.description}. {priceSummary(prices, isEmbedding ? ["input"] : METRIC_KEYS)}</p>
      </div>
      <div className="field">
        <label htmlFor="api-system">시스템 지시</label>
        <Input.TextArea id="api-system" value={isEmbedding ? "" : system} onChange={event => setSystem(event.target.value)} autoSize={{ minRows: 3, maxRows: 10 }} maxLength={8000} disabled={isSending || isEmbedding}
          placeholder={isEmbedding ? "임베딩 모델은 시스템 지시를 받지 않아요." : "모델이 지킬 역할과 규칙을 적어요."} aria-describedby="api-system-help" />
        <p id="api-system-help" className="field-help">{isEmbedding ? "임베딩 모델은 입력 토큰만 계산해요." : `약 ${formatTokens(systemTokens)}토큰. ${formatTokens(PROMPT_CACHE_MIN_TOKENS)}토큰 이상이면 캐시에 5분 동안 남고, 같은 지시로 다시 보내면 캐시 읽기 단가로 계산해요.`}</p>
      </div>
      <div className="field">
        <label htmlFor="api-message">{isEmbedding ? "임베딩할 글" : "메시지"}</label>
        <Input.TextArea id="api-message" value={prompt} onChange={event => { setPrompt(event.target.value); setError(""); }} autoSize={{ minRows: 4, maxRows: 12 }} maxLength={8000} disabled={isSending}
          placeholder={isEmbedding ? "검색이나 추천에 쓸 문장을 넣어요." : "모델에게 보낼 메시지를 적어요."} aria-invalid={Boolean(error)} aria-describedby="api-message-help" />
        <p id="api-message-help" className="field-help">{formatTokens(Array.from(prompt).length)} / 4,000자</p>
      </div>
      {error && <Notice>{error}</Notice>}
      <Button htmlType="submit" type="primary" loading={isSending} icon={<SendOutlined aria-hidden="true" />}>요청 보내기</Button>
    </form>
    <div className="api-output">
      <section className="panel" aria-labelledby="api-request-title">
        <h2 id="api-request-title">요청 본문</h2>
        <pre className="code-block" tabIndex={0} aria-label="요청 본문"><code>{requestPreview(modelId, isEmbedding ? "" : system, prompt, isEmbedding)}</code></pre>
      </section>
      <section className="panel" aria-labelledby="api-response-title" aria-live="polite">
        <h2 id="api-response-title">응답</h2>
        {!result ? <p className="empty-state">요청을 보내면 응답 JSON과 사용량이 여기에 나와요.</p> : <>
          {result.state === "completed" ? <>
            <TokenMeter usage={result.usage} label="이 요청의 사용량" />
            <p className="message-meta"><span>응답 {(result.latencyMs / 1000).toFixed(2)}초</span><MeteringLabel value={result.meteringStatus} /></p>
            <pre className="code-block code-block-response" tabIndex={0} aria-label="응답 JSON"><code>{responsePreview(result, isResultEmbedding)}</code></pre>
          </> : <p className="field-error">응답을 만들지 못했어요. 이 요청은 사용량에 넣지 않아요.</p>}
        </>}
      </section>
    </div>
  </div>;
}
