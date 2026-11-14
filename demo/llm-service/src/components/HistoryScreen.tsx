"use client";

import { ArrowRightOutlined, RightOutlined } from "@ant-design/icons";
import Link from "next/link";
import { useState } from "react";
import { MODELS, SURFACES } from "@/lib/catalog";
import type { GenerationView, WorkspaceSnapshot } from "@/lib/contracts";
import { MeteringLabel } from "./Feedback";
import { formatDate, formatTokens, surfaceName } from "./format";
import { TokenMeter } from "./TokenMeter";

function responseText(request: GenerationView): string {
  if (request.state === "processing") return "아직 처리하고 있어요. 잠시 뒤 새로 불러와 주세요.";
  if (request.state === "failed") return "응답을 만들지 못했어요. 사용량에 넣지 않은 요청이에요.";
  if (MODELS.find(model => model.id === request.modelId)?.kind === "embedding") return `${formatTokens((JSON.parse(request.output) as number[]).length)}차원 임베딩을 돌려줬어요.`;
  return request.output;
}

export function HistoryScreen({ snapshot }: { snapshot: WorkspaceSnapshot }) {
  const [surfaceFilter, setSurfaceFilter] = useState("");
  const [modelFilter, setModelFilter] = useState("");
  const [query, setQuery] = useState("");
  const needle = query.trim().toLocaleLowerCase();
  const filtered = snapshot.requests.filter(request => (!surfaceFilter || request.surface === surfaceFilter) && (!modelFilter || request.modelId === modelFilter)
    && (!needle || `${request.system} ${request.prompt} ${request.output}`.toLocaleLowerCase().includes(needle)));
  return <>
    <div className="history-filters">
      <label htmlFor="history-surface">용도<select id="history-surface" value={surfaceFilter} onChange={event => setSurfaceFilter(event.target.value)}><option value="">모든 용도</option>{SURFACES.map(surface => <option key={surface.id} value={surface.id}>{surface.name}</option>)}</select></label>
      <label htmlFor="history-model">모델<select id="history-model" value={modelFilter} onChange={event => setModelFilter(event.target.value)}><option value="">모든 모델</option>{MODELS.map(model => <option key={model.id} value={model.id}>{model.name}</option>)}</select></label>
      <label htmlFor="history-search">검색<input id="history-search" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="메시지나 응답 내용" /></label>
    </div>
    <p className="field-help" role="status">최근 요청 {formatTokens(snapshot.requests.length)}건 가운데 {formatTokens(filtered.length)}건</p>
    {filtered.length === 0 ? <div className="history-empty"><h2>{snapshot.requests.length === 0 ? "이달에는 요청 기록이 없어요" : "조건에 맞는 요청이 없어요"}</h2><p>{snapshot.requests.length === 0 ? "대화나 API, 일괄 처리로 요청을 보내면 메시지와 응답, 사용량이 함께 남아요." : "용도나 모델, 검색어를 바꿔 보세요."}</p>{snapshot.requests.length === 0 && <Link href="/" className="text-link">대화 시작하기 <ArrowRightOutlined aria-hidden="true" /></Link>}</div>
      : <div className="history-list">{filtered.map(request => <details className="history-record" key={request.id}>
        <summary>
          <span className="history-prompt">{request.prompt}</span>
          <span className="history-meta"><span className={`surface-tag surface-${request.surface}`}>{surfaceName(request.surface)}</span>{request.modelName}<span>{formatDate(request.createdAt)}</span></span>
          <span className="history-request-state">{request.state === "processing" ? "처리 중" : request.state === "failed" ? "실패" : <MeteringLabel value={request.meteringStatus} />}</span>
          <span className="history-disclosure"><span className="history-disclosure-closed">내용 보기</span><span className="history-disclosure-open">내용 접기</span><RightOutlined className="history-chevron" aria-hidden="true" /></span>
        </summary>
        <div className="history-content">
          {request.system && <><h3>시스템 지시</h3><p>{request.system}</p></>}
          <h3>보낸 메시지</h3><p>{request.prompt}</p>
          <h3>응답</h3><p>{responseText(request)}</p>
          {request.state === "completed" && <><TokenMeter usage={request.usage} isCompact /><p className="field-help">응답 {(request.latencyMs / 1000).toFixed(2)}초, 토큰 합계 {formatTokens(request.totalTokens)}</p></>}
        </div>
      </details>)}</div>}
  </>;
}
