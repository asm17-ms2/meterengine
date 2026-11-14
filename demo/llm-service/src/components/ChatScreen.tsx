"use client";

import { ArrowUpOutlined, LoadingOutlined, MenuOutlined, PlusOutlined } from "@ant-design/icons";
import { Button, Input } from "antd";
import type { GetRef } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import { defaultModelId, MODELS } from "@/lib/catalog";
import { PROMPT_MAX_LENGTH } from "@/lib/contracts";
import type { ConversationSummary, ConversationView, GenerationView, WorkspaceSnapshot } from "@/lib/contracts";
import { createRequestId } from "@/lib/request-id";
import { ApiFailure, errorMessage, jsonRequest, readApi, settleGeneration } from "./client-api";
import { MeteringLabel, Notice } from "./Feedback";
import { formatDate, formatShortDate, formatTokens, METRIC_KEYS, priceSummary } from "./format";
import { StreamingText } from "./StreamingText";
import { TokenMeter } from "./TokenMeter";

type PendingTurn = { requestId: string; conversationId: string; modelId: string; prompt: string };

const CHAT_MODELS = MODELS.filter(model => model.surfaces.includes("chat"));
const STARTERS = [
  "신규 고객에게 보낼 서비스 안내 메일 초안을 써 줘.",
  "아래 회의 내용을 다섯 줄로 요약해 줘.",
  "고객 문의를 유형별로 나누는 기준을 만들어 줘.",
] as const;

function AssistantMessage({ message, isAnimated }: { message: GenerationView; isAnimated: boolean }) {
  return <article className="message message-assistant" aria-label={`${message.modelName} 응답`}>
    <header><strong>{message.modelName}</strong><time dateTime={message.createdAt}>{formatDate(message.createdAt)}</time></header>
    {message.state === "completed" ? <StreamingText key={message.id} className="message-text" text={message.output} isAnimated={isAnimated} />
      : message.state === "failed" ? <p className="field-error">응답을 만들지 못했어요. 이 요청은 사용량에 넣지 않아요.</p>
      : <p className="loading-copy" role="status"><LoadingOutlined spin aria-hidden="true" /> 응답을 만들고 있어요.</p>}
    {message.state === "completed" && <footer>
      <TokenMeter usage={message.usage} isCompact />
      <p className="message-meta"><span>응답 {(message.latencyMs / 1000).toFixed(1)}초</span><MeteringLabel value={message.meteringStatus} /></p>
    </footer>}
  </article>;
}

export function ChatScreen({ snapshot, onUsageChanged, onSessionLost }: { snapshot: WorkspaceSnapshot; onUsageChanged: () => void; onSessionLost: () => void }) {
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);
  const [listError, setListError] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<GenerationView[]>([]);
  const [isLoadingConversation, setIsLoadingConversation] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [modelId, setModelId] = useState(defaultModelId("chat"));
  const [error, setError] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [sendingTurn, setSendingTurn] = useState<PendingTurn | null>(null);
  const [animatedId, setAnimatedId] = useState("");
  const [isListOpen, setIsListOpen] = useState(false);
  const [status, setStatus] = useState("");
  const pending = useRef<PendingTurn | null>(null);
  const logEnd = useRef<HTMLDivElement>(null);
  const textarea = useRef<GetRef<typeof Input.TextArea>>(null);

  const loadConversations = useCallback(async () => {
    try { setConversations((await readApi<{ conversations: ConversationSummary[] }>("/api/conversations")).conversations); setListError(""); }
    catch (problem) {
      setListError(errorMessage(problem, "대화 목록을 불러오지 못했어요. 잠시 뒤 다시 불러와 주세요."));
      if (problem instanceof ApiFailure && problem.status === 401) onSessionLost();
    }
  }, [onSessionLost]);

  const openConversation = useCallback(async (id: string) => {
    setIsLoadingConversation(true); setError("");
    try {
      const conversation = await readApi<ConversationView>(`/api/conversations/${encodeURIComponent(id)}`);
      setActiveId(conversation.id); setMessages(conversation.messages); setModelId(CHAT_MODELS.some(model => model.id === conversation.modelId) ? conversation.modelId : defaultModelId("chat"));
    } catch (problem) {
      setError(errorMessage(problem, "대화를 불러오지 못했어요. 목록에서 다시 골라 주세요."));
      if (problem instanceof ApiFailure && problem.status === 401) onSessionLost();
    } finally { setIsLoadingConversation(false); }
  }, [onSessionLost]);

  useEffect(() => {
    const controller = new AbortController();
    void readApi<{ conversations: ConversationSummary[] }>("/api/conversations", { signal: controller.signal })
      .then(result => setConversations(result.conversations))
      .catch(problem => { if (!controller.signal.aborted) setListError(errorMessage(problem, "대화 목록을 불러오지 못했어요. 잠시 뒤 다시 불러와 주세요.")); });
    return () => controller.abort();
  }, []);

  useEffect(() => { logEnd.current?.scrollIntoView({ block: "end" }); }, [messages.length, sendingTurn]);

  const startNew = () => {
    if (isSending) return;
    setActiveId(null); setMessages([]); setPrompt(""); setError(""); pending.current = null; setIsListOpen(false);
    textarea.current?.focus();
  };

  const send = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSending) return;
    const text = prompt.trim();
    if (!text || Array.from(text).length > PROMPT_MAX_LENGTH) { setError("메시지를 1자부터 4,000자까지 입력해 주세요."); textarea.current?.focus(); return; }
    const conversationId = activeId ?? pending.current?.conversationId ?? createRequestId();
    const previous = pending.current;
    const turn = previous && previous.prompt === text && previous.modelId === modelId && previous.conversationId === conversationId ? previous : { requestId: createRequestId(), conversationId, modelId, prompt: text };
    pending.current = turn;
    setIsSending(true); setSendingTurn(turn); setError(""); setStatus("응답을 만들고 있어요.");
    try {
      const first = await readApi<GenerationView>("/api/generations", jsonRequest({ requestId: turn.requestId, surface: "chat", conversationId, modelId, prompt: text }));
      const result = await settleGeneration(first);
      if (result.state === "failed") { setError("응답을 만들지 못했어요. 같은 메시지를 다시 보내면 이어서 처리해요."); setStatus(""); }
      else { pending.current = null; setPrompt(""); setAnimatedId(result.id); setStatus("응답이 도착했어요."); }
      const conversation = await readApi<ConversationView>(`/api/conversations/${encodeURIComponent(conversationId)}`);
      setActiveId(conversation.id); setMessages(conversation.messages);
      void loadConversations();
      onUsageChanged();
    } catch (problem) {
      setError(errorMessage(problem, "응답을 받았는지 확인하지 못했어요. 같은 메시지를 다시 보내면 중복 없이 이어서 확인해요."));
      setStatus("");
      if (problem instanceof ApiFailure && problem.status === 401) onSessionLost();
    } finally { setIsSending(false); setSendingTurn(null); }
  };

  const onComposerKey = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  };

  const prices = snapshot.models.find(model => model.id === modelId)?.prices.chat;
  const selectedModel = CHAT_MODELS.find(model => model.id === modelId);
  const activeTitle = conversations?.find(item => item.id === activeId)?.title;
  const isEmpty = messages.length === 0 && !sendingTurn;

  return <div className="chat-layout">
    <aside className={`chat-sidebar${isListOpen ? " is-open" : ""}`} aria-label="대화 목록">
      <div className="chat-sidebar-actions">
        <Button type="primary" icon={<PlusOutlined aria-hidden="true" />} onClick={startNew} disabled={isSending}>새 대화</Button>
        <button type="button" className="chat-list-toggle" aria-expanded={isListOpen} aria-controls="conversation-list" onClick={() => setIsListOpen(value => !value)}><MenuOutlined aria-hidden="true" /> 지난 대화 {conversations ? `${conversations.length}개` : ""}</button>
      </div>
      <div id="conversation-list" className="chat-list">
        <h2>지난 대화</h2>
        {listError ? <p className="field-error" role="alert">{listError}</p>
          : conversations === null ? <p className="field-help" role="status">대화 목록을 불러오고 있어요.</p>
          : conversations.length === 0 ? <p className="field-help">아직 대화가 없어요. 메시지를 보내면 여기에 저장돼요.</p>
          : <ul>{conversations.map(item => <li key={item.id}><button type="button" aria-current={item.id === activeId ? "true" : undefined} disabled={isSending} onClick={() => { setIsListOpen(false); void openConversation(item.id); }}>
            <span>{item.title}</span><small>{formatShortDate(item.updatedAt)}, {formatTokens(item.turns)}차례</small>
          </button></li>)}</ul>}
      </div>
    </aside>
    <section className="chat-pane" aria-labelledby="chat-title">
      <div className="chat-toolbar">
        <h1 id="chat-title" className="chat-title">{activeTitle ?? "새 대화"}</h1>
        <div className="chat-model">
          <label htmlFor="chat-model">모델</label>
          <select id="chat-model" value={modelId} onChange={event => { setModelId(event.target.value); setError(""); }} disabled={isSending}>{CHAT_MODELS.map(model => <option key={model.id} value={model.id}>{model.name}</option>)}</select>
        </div>
        <p className="chat-model-note">{selectedModel?.description}. {priceSummary(prices, METRIC_KEYS)}</p>
      </div>
      <div className="chat-log" role="log" aria-label="대화 내용" aria-busy={isSending || isLoadingConversation}>
        {isLoadingConversation ? <p className="loading-copy" role="status"><LoadingOutlined spin aria-hidden="true" /> 대화를 불러오고 있어요.</p>
          : isEmpty ? <div className="chat-empty">
            <h2>무엇을 도와드릴까요?</h2>
            <p>메시지를 보내면 대화가 시작되고 지난 대화 목록에 저장돼요. 이어서 물으면 앞선 대화는 캐시에서 읽어 더 싸게 계산해요.</p>
            <div className="chat-starters" aria-label="메시지 예시">{STARTERS.map(starter => <button key={starter} type="button" onClick={() => { setPrompt(starter); textarea.current?.focus(); }}>{starter}</button>)}</div>
          </div>
          : <>{messages.map(message => <div key={message.id} className="turn">
            <div className="message message-user"><p>{message.prompt}</p></div>
            <AssistantMessage message={message} isAnimated={message.id === animatedId} />
          </div>)}
          {sendingTurn && !messages.some(message => message.id === sendingTurn.requestId) && <div className="turn">
            <div className="message message-user"><p>{sendingTurn.prompt}</p></div>
            <div className="message message-assistant"><p className="loading-copy" role="status"><LoadingOutlined spin aria-hidden="true" /> 응답을 만들고 있어요.</p></div>
          </div>}</>}
        <div ref={logEnd} className="chat-log-end" />
      </div>
      <form className="chat-composer" onSubmit={event => void send(event)} aria-busy={isSending}>
        {error && <div className="composer-error"><Notice>{error}</Notice></div>}
        <label htmlFor="chat-message" className="screen-reader-only">메시지</label>
        <div className="composer-box">
          <Input.TextArea id="chat-message" ref={textarea} value={prompt} onChange={event => { setPrompt(event.target.value); setError(""); }} onKeyDown={onComposerKey}
            autoSize={{ minRows: 1, maxRows: 8 }} maxLength={8000} placeholder={`${selectedModel?.name ?? "모델"}에게 메시지 보내기`} disabled={isSending}
            aria-invalid={Boolean(error)} aria-describedby="chat-hint" />
          <Button htmlType="submit" type="primary" shape="circle" loading={isSending} icon={<ArrowUpOutlined aria-hidden="true" />} aria-label="보내기" />
        </div>
        <p id="chat-hint" className="field-help">Enter로 보내고 Shift+Enter로 줄을 바꿔요. {formatTokens(Array.from(prompt).length)} / 4,000자</p>
        <p className="screen-reader-only" role="status" aria-live="polite">{status}</p>
      </form>
    </section>
  </div>;
}
