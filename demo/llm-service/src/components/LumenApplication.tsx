"use client";

import { ArrowRightOutlined, CheckOutlined, CopyOutlined, LoadingOutlined, LogoutOutlined, ReloadOutlined, RightOutlined, SendOutlined } from "@ant-design/icons";
import { Button, ConfigProvider, Input } from "antd";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { METRIC_CODES, MODEL_CATALOG } from "@/lib/contracts";
import type { ApiErrorBody, BillingLine, GenerationView, InvoiceView, LlmModel, SessionResponse, WorkspaceSnapshot } from "@/lib/contracts";
import { currentBillingMonth, workspaceBillingMonth } from "./billing-month";
import type { WorkspaceSection } from "./billing-month";

type Section = WorkspaceSection;
type PendingRequest = { requestId: string; modelId: string; prompt: string };
type RegistrationView = { url: string; expiresAt: string; mode: "test" | "live" };

const NAV_ITEMS = [
  { id: "playground", label: "플레이그라운드", href: "/" },
  { id: "usage", label: "사용량", href: "/usage" },
  { id: "billing", label: "청구 내역", href: "/billing" },
  { id: "history", label: "요청 기록", href: "/history" },
] as const;
const MONEY = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 0 });
const QUANTITY = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 12 });
const DATE_TIME = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false });
const PAYMENT_LABELS: Record<string, string> = { not_required: "납부할 금액 없음", unpaid: "납부 대기", pending: "결제 확인 중", paid: "납부 완료", failed: "결제 실패", partially_canceled: "일부 취소", canceled: "전액 취소" };
const SAMPLE_PROMPTS = [
  { title: "첫 문장을 가볍게", prompt: "작은 팀이 사용할 AI 메모 서비스의 소개 문구를 세 가지 제안해 줘." },
  { title: "복잡한 내용을 짧게", prompt: "사용량 기반 과금이 무엇인지 처음 사용하는 사람에게 쉽게 설명해 줘." },
  { title: "계획을 구체적으로", prompt: "새 프로젝트의 첫 주에 할 일을 하루 단위로 나눠서 정리해 줘." },
] as const;
const LUMEN_THEME = {
  token: { colorPrimary: "#146c94", colorText: "#17344f", colorTextSecondary: "#526879", colorBgContainer: "#ffffff", colorBorder: "#becdd7", borderRadius: 8, controlHeight: 44, fontSize: 15, fontFamily: '-apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif' },
};

class ApiFailure extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function readApi<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, { ...options, credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json", ...options.headers } });
  const body: unknown = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const error = body && typeof body === "object" && "error" in body ? (body as ApiErrorBody).error : null;
    throw new ApiFailure(response.status, typeof error?.code === "string" ? error.code : "request_failed", typeof error?.message === "string" ? error.message : "요청을 마치지 못했습니다. 잠시 후 다시 시도하세요.");
  }
  return body as T;
}

function jsonRequest(body: unknown): RequestInit {
  return { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}

function formatQuantity(value: number): string {
  if (value !== 0 && Math.abs(value) < 0.0000000000005) return value.toExponential(6);
  return QUANTITY.format(value);
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "시각 확인 필요" : DATE_TIME.format(date);
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiFailure ? error.message : fallback;
}

function modelName(modelId: string): string {
  return MODEL_CATALOG.find(model => model.id === modelId)?.name ?? "이전 모델";
}

function MeteringLabel({ value }: { value: GenerationView["meteringStatus"] }) {
  const label = value === "delivered" ? "사용량 반영됨" : value === "failed" ? "사용량 확인 필요" : "사용량 반영 대기";
  return <span className={`state-label state-label-${value}`}>{value === "delivered" && <CheckOutlined aria-hidden="true" />}{label}</span>;
}

function Notice({ children, onRetry, isBusy = false, title }: { children: React.ReactNode; onRetry?: () => void; isBusy?: boolean; title?: string }) {
  return <div className="notice" role="alert"><div>{title && <strong>{title}</strong>}<p>{children}</p></div>{onRetry && <Button onClick={onRetry} loading={isBusy} icon={<ReloadOutlined aria-hidden="true" />}>다시 불러오기</Button>}</div>;
}

function MonthFilter({ value, onApply, isBusy }: { value: string; onApply: (value: string) => void; isBusy: boolean }) {
  const [draft, setDraft] = useState(value);
  const [error, setError] = useState("");
  return <form className="month-filter" onSubmit={event => {
    event.preventDefault();
    if (!/^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(draft)) { setError("조회할 월을 선택하세요."); return; }
    setError(""); onApply(draft);
  }}><label htmlFor="lumen-month">조회 월</label><div><input id="lumen-month" name="month" type="month" value={draft} onChange={event => setDraft(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={error ? "month-error" : "month-hint"} required /><Button htmlType="submit" loading={isBusy}>적용</Button></div>{error ? <span id="month-error" className="field-error">{error}</span> : <span id="month-hint" className="field-help">한국 시간 기준</span>}</form>;
}

function LoginScreen({ session, error, isBusy, onLogin, onReload }: { session: SessionResponse | null; error: string; isBusy: boolean; onLogin: (accountId: string) => void; onReload: () => void }) {
  const [accountId, setAccountId] = useState("");
  const accounts = session?.accounts ?? [];
  const selected = accountId || accounts[0]?.id || "";
  return <main id="main-content" className="welcome">
    <div className="welcome-story"><Link className="wordmark" href="/" aria-label="Lumen 홈">Lumen<span>나의 AI 작업 공간</span></Link><h1>작은 아이디어를<br />다음 초안으로.</h1><p className="welcome-description">모델을 고르고 대화를 시작하세요.<br />어떤 작업을 했는지, 얼마나 사용했는지 한곳에 남습니다.</p><div className="welcome-path"><span>대화 만들기</span><ArrowRightOutlined aria-hidden="true" /><span>사용량 확인</span><ArrowRightOutlined aria-hidden="true" /><span>청구 확인</span></div><p className="welcome-note">Lumen은 도입사 서비스 예제입니다. 모델 응답은 로컬 모형이며, 사용량과 요금은 실제 MeterEngine 연동 결과입니다.</p></div>
    <section className="welcome-signin" aria-labelledby="signin-title"><h2 id="signin-title">직접 사용해 보세요</h2><p>체험 계정마다 대화와 사용 내역이 따로 보관됩니다.</p>
      {session === null && !error ? <div className="loading-copy" role="status"><LoadingOutlined spin aria-hidden="true" /> 체험 계정을 불러오고 있습니다.</div> : !session?.configured ? <Notice title={error ? "서비스에 연결하지 못했습니다" : "서비스 연동을 준비하고 있습니다"} onRetry={onReload}>{error || "체험 계정과 요금 설정이 준비되면 이곳에서 시작할 수 있습니다."}</Notice> : <form onSubmit={event => { event.preventDefault(); if (selected) onLogin(selected); }}>
        <label className="field-label" htmlFor="lumen-account">체험 계정</label><select id="lumen-account" name="accountId" value={selected} onChange={event => setAccountId(event.target.value)} disabled={isBusy}>{accounts.map(account => <option key={account.id} value={account.id}>{account.name} - {account.email}</option>)}</select>
        {accounts.find(account => account.id === selected) && <p className="account-help">{accounts.find(account => account.id === selected)!.name}의 작업 공간으로 들어갑니다.</p>}
        {error && <p className="field-error" role="alert">{error}</p>}
        <Button type="primary" htmlType="submit" block loading={isBusy} disabled={!selected} icon={<ArrowRightOutlined aria-hidden="true" />} iconPlacement="end">체험 시작</Button>
      </form>}
      <p className="demo-identity-note">시연용 계정 선택 화면입니다. 실제 서비스의 가입과 본인 인증을 대신하지 않습니다.</p>
    </section>
  </main>;
}

function ModelPrices({ model }: { model: LlmModel | undefined }) {
  const price = (value: number | null | undefined) => value == null ? "조회할 수 없음" : MONEY.format(value);
  return <dl className="model-prices"><div><dt>입력 1,000 토큰</dt><dd>{price(model?.inputPricePerThousand)}</dd></div><div><dt>출력 1,000 토큰</dt><dd>{price(model?.outputPricePerThousand)}</dd></div><div><dt>요청 1회</dt><dd>{price(model?.requestPrice)}</dd></div></dl>;
}

function SyncStatus({ snapshot, isBusy, onRetry }: { snapshot: WorkspaceSnapshot; isBusy: boolean; onRetry: () => void }) {
  const total = snapshot.stats.pendingMetering + snapshot.stats.failedMetering;
  if (total === 0) return <p className="sync-ready"><CheckOutlined aria-hidden="true" /> {snapshot.stats.totalRequests > 0 ? "완료된 요청의 사용량이 모두 반영됐습니다." : "첫 응답을 만들면 사용량이 반영됩니다."}</p>;
  return <div className="sync-pending" role="status"><div><strong>사용량 {formatQuantity(total)}건의 반영을 기다리고 있습니다.</strong><p>대화 결과는 저장됐습니다. 같은 사용량을 다시 보내도 중복 청구하지 않습니다.{snapshot.stats.failedMetering > 0 && ` 확인이 필요한 요청은 ${formatQuantity(snapshot.stats.failedMetering)}건입니다.`}</p></div><Button onClick={onRetry} loading={isBusy}>사용량 다시 보내기</Button></div>;
}

function GenerationOutput({ generation, isBusy }: { generation: GenerationView | null; isBusy: boolean }) {
  const [copiedId, setCopiedId] = useState("");
  const [copyError, setCopyError] = useState("");
  return <section className={`conversation ${generation ? "has-response" : ""}`} role="region" aria-label="모델 응답" aria-busy={isBusy}>
    {!generation ? <div className="conversation-empty">{isBusy ? <><h2>응답을 준비하고 있습니다</h2><p role="status"><LoadingOutlined spin aria-hidden="true" /> 메시지를 처리하고 있습니다.</p></> : <><h2>어떤 작업을 시작할까요?</h2><p>짧은 질문부터 구체적인 업무 초안까지,<br />아래에 메시지를 적어 주세요.</p><span className="empty-caption">대화 내용은 이 체험 계정의 요청 기록에 저장됩니다.</span></>}</div> : <>
      <div className="prompt-message"><span>보낸 메시지</span><p>{generation.prompt}</p></div>
      <div className="response-message"><header><div><h2>{generation.modelName}</h2><p>{formatDate(generation.createdAt)}</p></div>{generation.state === "completed" && <Button icon={copiedId === generation.id ? <CheckOutlined aria-hidden="true" /> : <CopyOutlined aria-hidden="true" />} onClick={async () => {
        try { await navigator.clipboard.writeText(generation.output); setCopiedId(generation.id); setCopyError(""); }
        catch { setCopyError("복사하지 못했습니다. 응답 내용을 직접 선택해 복사하세요."); }
      }}>{copiedId === generation.id ? "복사됨" : "응답 복사"}</Button>}</header>
        {generation.state === "processing" ? <p className="loading-copy" role="status"><LoadingOutlined spin aria-hidden="true" /> 응답을 준비하고 있습니다.</p> : generation.state === "failed" ? <p className="field-error" role="alert">응답을 만들지 못했습니다. 이 요청은 사용량에 포함하지 않습니다.</p> : <div className="response-text">{generation.output}</div>}
        <span className="screen-reader-only" role="status" aria-live="polite">{copiedId === generation.id ? "응답을 복사했습니다." : ""}</span>
        {copyError && <p className="field-error" role="alert">{copyError}</p>}
        {generation.state === "completed" && <footer><span>입력 {formatQuantity(generation.inputTokens)} / 출력 {formatQuantity(generation.outputTokens)} 토큰</span><MeteringLabel value={generation.meteringStatus} /></footer>}
      </div>
    </>}
  </section>;
}

function Playground({ snapshot, generation, prompt, modelId, error, isGenerating, isRetrying, isRefreshing, hasUncertainRequest, onPromptChange, onModelChange, onSubmit, onRetryMetering, onRefresh, onSelectRequest }: {
  snapshot: WorkspaceSnapshot; generation: GenerationView | null; prompt: string; modelId: string; error: string; isGenerating: boolean; isRetrying: boolean; isRefreshing: boolean; hasUncertainRequest: boolean;
  onPromptChange: (value: string) => void; onModelChange: (value: string) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; onRetryMetering: () => void; onRefresh: () => void; onSelectRequest: (request: GenerationView) => void;
}) {
  const selected = snapshot.models.find(model => model.id === modelId);
  return <><div className="page-heading"><div><h1>플레이그라운드</h1><p>메시지를 쓰고, 응답을 다듬고, 사용한 만큼 확인하세요.</p></div><span className="demo-mode">로컬 시연 모델</span></div>
    {(snapshot.usage.state === "unavailable" || snapshot.billing.state === "unavailable") && <Notice title="일부 사용 정보를 확인하지 못했습니다" onRetry={onRefresh} isBusy={isRefreshing}>
      {snapshot.usage.state === "unavailable" && <span className="notice-detail">사용량: {snapshot.usage.error || "사용량을 확인할 수 없습니다. 잠시 후 다시 불러오세요."}</span>}
      {snapshot.billing.state === "unavailable" && <span className="notice-detail">청구: {snapshot.billing.error || "일부 청구 정보를 확인할 수 없습니다. 잠시 후 다시 불러오세요."}</span>}
      <span className="notice-detail">대화와 요청 기록은 유지됩니다. 아래에는 조회에 성공한 자료만 표시합니다.</span>
    </Notice>}
    <div className="studio-layout"><div className="studio-main"><div className="model-picker"><label htmlFor="lumen-model">모델</label><select id="lumen-model" value={modelId} onChange={event => onModelChange(event.target.value)} disabled={isGenerating}>{snapshot.models.map(model => <option key={model.id} value={model.id}>{model.name}</option>)}</select><span>두 모델 모두 로컬 시연 응답입니다.</span></div><GenerationOutput generation={generation} isBusy={isGenerating} />
      {!generation && !isGenerating && <div className="sample-prompts" aria-label="메시지 예시">{SAMPLE_PROMPTS.map(sample => <button key={sample.title} type="button" onClick={() => { onPromptChange(sample.prompt); document.getElementById("lumen-prompt")?.focus(); }} disabled={isGenerating}><span>{sample.title}</span><ArrowRightOutlined aria-hidden="true" /></button>)}</div>}
      <form className="composer" onSubmit={onSubmit} aria-busy={isGenerating}><div className="composer-heading"><label htmlFor="lumen-prompt">메시지</label><span id="prompt-count">{formatQuantity(Array.from(prompt).length)} / 4,000자</span></div><Input.TextArea id="lumen-prompt" name="prompt" value={prompt} onChange={event => onPromptChange(event.target.value)} autoSize={{ minRows: 4, maxRows: 12 }} maxLength={8000} placeholder="무엇을 만들고 싶은지 편하게 적어 주세요." disabled={isGenerating} aria-invalid={Boolean(error)} aria-describedby={error ? "generation-error prompt-help" : "prompt-help prompt-count"} />
        <div className="composer-actions"><p id="prompt-help">입력과 응답 문자를 시연용 토큰으로 계산합니다.</p><Button htmlType="submit" type="primary" loading={isGenerating} icon={<SendOutlined aria-hidden="true" />}>{hasUncertainRequest ? "같은 요청 다시 확인" : "응답 생성"}</Button></div>{error && <p id="generation-error" className="field-error" role="alert">{error}</p>}
      </form>
    </div><aside className="studio-context" aria-label="모델과 내 사용 현황"><section className="model-panel"><h2>{selected?.name ?? "선택한 모델"}</h2><p className="model-description">{selected?.description}</p><ModelPrices model={selected} /><p className="field-help">부가세 별도. 청구 항목마다 10원 미만을 버립니다.</p></section>
      <section className="account-summary"><div className="section-title"><h2>이번 달 사용</h2><Link href="/usage">자세히 보기</Link></div><dl><div><dt>완료한 요청</dt><dd>{formatQuantity(snapshot.stats.totalRequests)}회</dd></div><div><dt>청구 예정액</dt><dd>{snapshot.billing.draft ? MONEY.format(snapshot.billing.draft.totalAmount) : snapshot.billing.state === "unavailable" ? "조회할 수 없음" : "예정액 없음"}</dd></div></dl><p className="field-help">청구 예정액은 부가세 포함 금액입니다.</p></section>
      <section className="recent-requests"><div className="section-title"><h2>최근 요청</h2><Link href="/history">전체 보기</Link></div>{snapshot.requests.length === 0 ? <p className="field-help">첫 응답을 만들면 여기에 남습니다.</p> : <ul>{snapshot.requests.slice(0, 3).map(request => <li key={request.id}><button onClick={() => onSelectRequest(request)} type="button" disabled={isGenerating} aria-label={`요청 보기: ${request.prompt.slice(0, 48)}`}><span>{request.prompt}</span><small>{formatDate(request.createdAt)}</small></button></li>)}</ul>}</section>
    </aside></div><SyncStatus snapshot={snapshot} isBusy={isRetrying} onRetry={onRetryMetering} /><p className="provider-note">{snapshot.provider.tokenMethod} 모델 응답을 위한 외부 API 호출은 없습니다.</p>
  </>;
}

function UsageScreen({ snapshot, isRetrying, onRetryMetering }: { snapshot: WorkspaceSnapshot; isRetrying: boolean; onRetryMetering: () => void }) {
  const input = snapshot.usage.metrics.find(metric => metric.code === METRIC_CODES.input);
  const output = snapshot.usage.metrics.find(metric => metric.code === METRIC_CODES.output);
  const requests = snapshot.usage.metrics.find(metric => metric.code === METRIC_CODES.requests);
  const raw = (code: string, modelId: string) => snapshot.usage.metrics.find(metric => metric.code === code)?.modelQuantities.find(model => model.modelId === modelId)?.rawQuantity ?? 0;
  return <>
    {snapshot.usage.state === "unavailable" && <Notice title="사용량을 확인하지 못했습니다">{snapshot.usage.error || "잠시 후 다시 불러오세요. 확인되지 않은 사용량을 0으로 표시하지 않습니다."}</Notice>}
    <section className="usage-ledger" aria-labelledby="usage-ledger-title"><div className="section-title"><h2 id="usage-ledger-title">내 사용량</h2><span className="field-help">{formatDate(snapshot.usage.updatedAt)} 조회</span></div>
      {snapshot.usage.state === "ready" ? <dl className="usage-totals"><div><dt>입력 토큰</dt><dd>{formatQuantity(input?.rawQuantity ?? 0)}<small>시연용 토큰</small></dd></div><div><dt>출력 토큰</dt><dd>{formatQuantity(output?.rawQuantity ?? 0)}<small>시연용 토큰</small></dd></div><div><dt>반영된 요청</dt><dd>{formatQuantity(requests?.rawQuantity ?? 0)}<small>회</small></dd></div></dl> : <p className="empty-state">사용량 조회 결과를 기다리고 있습니다.</p>}
      <p className="field-help">서버에 반영된 사용량입니다. 대기 중인 요청은 아래에서 다시 보낼 수 있습니다.</p>
    </section>
    <SyncStatus snapshot={snapshot} isBusy={isRetrying} onRetry={onRetryMetering} />
    {snapshot.usage.state === "ready" && <section className="data-section"><div className="section-title"><h2>모델별 사용량</h2><span className="field-help">동일한 조회 월 기준</span></div><div className="table-scroll"><table className="usage-table"><thead><tr><th scope="col">모델</th><th scope="col">입력 토큰</th><th scope="col">출력 토큰</th><th scope="col">요청 수</th></tr></thead><tbody>{snapshot.models.map(model => <tr key={model.id}><th scope="row">{model.name}</th><td>{formatQuantity(raw(METRIC_CODES.input, model.id))}</td><td>{formatQuantity(raw(METRIC_CODES.output, model.id))}</td><td>{formatQuantity(raw(METRIC_CODES.requests, model.id))}</td></tr>)}</tbody></table></div></section>}
    <section className="data-section"><h2>적용 요금</h2><p className="section-description">모델마다 입력, 출력, 요청 요금을 함께 계산합니다. 부가세는 청구할 때 별도로 더합니다.</p><div className="pricing-comparison">{snapshot.models.map(model => <article key={model.id}><h3>{model.name}</h3><p>{model.description}</p><ModelPrices model={model} /></article>)}</div></section><p className="provider-note">{snapshot.provider.tokenMethod}</p>
  </>;
}

function BillingLines({ lines }: { lines: BillingLine[] }) {
  if (lines.length === 0) return <p className="empty-state">아직 청구할 사용 항목이 없습니다.</p>;
  return <div className="billing-lines">{lines.map((line, index) => <article key={`${line.metricCode}-${line.modelId}-${index}`} className="billing-line"><div className="line-heading"><div><h4>{line.label}</h4>{line.modelId && <p>{modelName(line.modelId)}</p>}</div><strong>{MONEY.format(line.amount)}</strong></div><dl><div><dt>과금 수량</dt><dd>{formatQuantity(line.quantity)}</dd></div><div><dt>단가</dt><dd>{MONEY.format(line.unitPrice)}</dd></div></dl>{line.quantityNumerator != null && line.quantityDenominator != null && line.quantityDenominator !== 1 && <p className="fraction-basis">계산 기준: {formatQuantity(line.quantityNumerator)} / {formatQuantity(line.quantityDenominator)}</p>}</article>)}</div>;
}

function InvoiceDetails({ invoice }: { invoice: InvoiceView }) {
  return <article className="invoice-record"><header><div><h3>{invoice.month} 청구서</h3><p>{formatDate(invoice.finalizedAt)} 확정</p></div><span className={`payment-label payment-label-${invoice.status}`}>{PAYMENT_LABELS[invoice.status] ?? "상태 확인 필요"}</span></header><dl className="invoice-amounts"><div><dt>청구 금액</dt><dd>{MONEY.format(invoice.totalAmount)}</dd></div><div><dt>납부액</dt><dd>{invoice.paidAmount === null ? "조회할 수 없음" : MONEY.format(invoice.paidAmount)}</dd></div><div><dt>취소액</dt><dd>{invoice.canceledAmount === null ? "조회할 수 없음" : MONEY.format(invoice.canceledAmount)}</dd></div><div><dt>납부 대기액</dt><dd>{invoice.amountDue === null ? "조회할 수 없음" : MONEY.format(invoice.amountDue)}</dd></div></dl>{invoice.canceledAmount !== null && invoice.canceledAmount > 0 && <p className="field-help">취소한 금액은 납부 대기액과 구분해 표시합니다. 재청구는 별도 작업으로 처리합니다.</p>}<details><summary>요금 계산 내역</summary><BillingLines lines={invoice.lines} /><dl className="statement-totals"><div><dt>공급가액</dt><dd>{MONEY.format(invoice.supplyAmount)}</dd></div><div><dt>부가세</dt><dd>{MONEY.format(invoice.taxAmount)}</dd></div><div><dt>합계</dt><dd>{MONEY.format(invoice.totalAmount)}</dd></div></dl></details></article>;
}

function BillingScreen({ snapshot }: { snapshot: WorkspaceSnapshot }) {
  const [isRegistering, setIsRegistering] = useState(false);
  const [registration, setRegistration] = useState<RegistrationView | null>(null);
  const [error, setError] = useState("");
  const billing = snapshot.billing;
  const startRegistration = async () => {
    setIsRegistering(true); setError(""); setRegistration(null);
    try { setRegistration(await readApi<RegistrationView>("/api/payment-method", jsonRequest({}))); }
    catch (problem) { setError(errorMessage(problem, "카드 등록을 시작하지 못했습니다. 잠시 후 다시 시도하세요.")); }
    finally { setIsRegistering(false); }
  };
  return <>
    {billing.state === "unavailable" && <Notice title="일부 청구 정보를 확인하지 못했습니다">{billing.error || "확인된 항목만 표시하고 있습니다. 다시 불러오면 최신 상태를 확인할 수 있습니다."}</Notice>}
    <div className="billing-layout"><div><section className="statement" aria-labelledby="draft-title"><header><div><h2 id="draft-title">청구 예정액</h2><p>아직 확정되지 않은 사용 요금입니다.</p></div><span className="statement-status">확정 전</span></header>{billing.draft ? <><p className="statement-total">{MONEY.format(billing.draft.totalAmount)}<span>부가세 포함</span></p><details><summary>예정 요금 계산 내역</summary><BillingLines lines={billing.draft.lines} /><dl className="statement-totals"><div><dt>공급가액</dt><dd>{MONEY.format(billing.draft.supplyAmount)}</dd></div><div><dt>부가세</dt><dd>{MONEY.format(billing.draft.taxAmount)}</dd></div></dl></details></> : <p className="statement-empty">{billing.state === "unavailable" ? "청구 예정액을 확인할 수 없습니다." : "이 달에는 아직 청구 예정액이 없습니다."}</p>}<p className="field-help">각 사용 항목의 10원 미만을 버린 뒤 부가세 10%를 더합니다. 사용량 반영이나 요금 확정에 따라 달라질 수 있습니다.</p></section>
      <section className="data-section"><h2>확정된 청구서</h2>{billing.invoices.length === 0 ? <p className="empty-state">{billing.state === "unavailable" ? "청구서 조회 결과를 확인하지 못했습니다." : "이 달에 확정된 청구서가 없습니다."}</p> : billing.invoices.map(invoice => <InvoiceDetails key={invoice.id} invoice={invoice} />)}</section>
    </div><aside className="payment-method"><h2>결제 카드</h2><p className="card-number">{billing.paymentMethod ? billing.paymentMethod.cardNumber : billing.state === "unavailable" ? "카드 정보를 확인할 수 없습니다" : "등록된 카드가 없습니다"}</p><p>{billing.paymentMethod?.archived ? "현재 이 카드는 자동결제에 사용하지 않습니다. 다시 사용할 카드를 등록하세요." : billing.paymentMethod ? "다음 청구에 사용할 카드입니다. 카드를 바꾸려면 다시 인증하세요." : "청구에 사용할 자동결제 카드를 등록할 수 있습니다."}</p>
      {billing.paymentMethod && <p className="field-help">{formatDate(billing.paymentMethod.authenticatedAt)} 인증</p>}
      {billing.canRegisterPaymentMethod ? <Button onClick={() => void startRegistration()} loading={isRegistering}>{billing.paymentMethod ? "결제 카드 변경" : "카드 등록 시작"}</Button> : <p className="field-help">이 체험 환경에서는 카드 등록이 준비되지 않았습니다.</p>}
      {error && <p className="field-error" role="alert">{error}</p>}
      {registration && <div className="registration-ready" role="status"><strong>{registration.mode === "test" ? "테스트 카드 등록을 시작할 수 있습니다" : "카드 등록을 시작할 수 있습니다"}</strong><p>다음 화면에서 동의 내용을 확인하고 카드 인증을 완료하세요. {formatDate(registration.expiresAt)}까지 한 번 사용할 수 있습니다.</p><a className="text-link" href={registration.url}>카드 등록 화면으로 이동 <ArrowRightOutlined aria-hidden="true" /></a></div>}
      <p className="field-help">카드 등록과 결제 실행은 구분됩니다. 이 화면에서 결제를 자동으로 실행하지 않습니다.</p>
    </aside></div>
  </>;
}

function HistoryScreen({ snapshot }: { snapshot: WorkspaceSnapshot }) {
  const [modelFilter, setModelFilter] = useState("");
  const [query, setQuery] = useState("");
  const filtered = snapshot.requests.filter(request => (!modelFilter || request.modelId === modelFilter) && (!query.trim() || `${request.prompt} ${request.output}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())));
  return <><div className="history-filters"><label htmlFor="history-model">모델<select id="history-model" value={modelFilter} onChange={event => setModelFilter(event.target.value)}><option value="">모든 모델</option>{snapshot.models.map(model => <option key={model.id} value={model.id}>{model.name}</option>)}</select></label><label htmlFor="history-search">요청 검색<input id="history-search" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="메시지 또는 응답 내용" /></label></div><p className="field-help" role="status">불러온 최근 요청 {formatQuantity(snapshot.requests.length)}건 중 {formatQuantity(filtered.length)}건</p>
    {filtered.length === 0 ? <div className="history-empty"><h2>{snapshot.requests.length === 0 ? "아직 요청 기록이 없습니다" : "검색에 맞는 요청이 없습니다"}</h2><p>{snapshot.requests.length === 0 ? "첫 대화를 만들면 메시지와 응답, 사용량이 함께 남습니다." : "모델이나 검색어를 바꿔 보세요."}</p>{snapshot.requests.length === 0 && <Link href="/" className="text-link">첫 대화 만들기 <ArrowRightOutlined aria-hidden="true" /></Link>}</div> : <div className="history-list">{filtered.map(request => <details className="history-record" key={request.id}><summary><span className="history-prompt">{request.prompt}</span><span className="history-meta">{request.modelName}<span>{formatDate(request.createdAt)}</span></span><span className="history-request-state">{request.state === "processing" ? "응답 생성 중" : request.state === "failed" ? "생성 실패" : <MeteringLabel value={request.meteringStatus} />}</span><span className="history-disclosure"><span className="history-disclosure-closed">내용 보기</span><span className="history-disclosure-open">내용 접기</span><RightOutlined className="history-chevron" aria-hidden="true" /></span></summary><div className="history-content"><h3>보낸 메시지</h3><p>{request.prompt}</p><h3>모델 응답</h3><p>{request.state === "processing" ? "아직 처리 중입니다. 잠시 후 새로 불러오세요." : request.state === "failed" ? "응답을 만들지 못했습니다. 사용량에 포함하지 않은 요청입니다." : request.output}</p>{request.state === "completed" && <p className="field-help">입력 {formatQuantity(request.inputTokens)}, 출력 {formatQuantity(request.outputTokens)}, 합계 {formatQuantity(request.totalTokens)} 시연용 토큰</p>}</div></details>)}</div>}
  </>;
}

export function LumenApplication({ section, initialMonth }: { section: Section; initialMonth?: string }) {
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [sessionError, setSessionError] = useState("");
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [snapshot, setSnapshot] = useState<WorkspaceSnapshot | null>(null);
  const [workspaceError, setWorkspaceError] = useState("");
  const [isRefreshing, setIsRefreshing] = useState(true);
  const [month, setMonth] = useState(() => workspaceBillingMonth(section, initialMonth ?? currentBillingMonth()));
  const [prompt, setPrompt] = useState("");
  const [modelId, setModelId] = useState<string>(MODEL_CATALOG[0].id);
  const [generation, setGeneration] = useState<GenerationView | null>(null);
  const [generationError, setGenerationError] = useState("");
  const [isGenerating, setIsGenerating] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false);
  const [actionStatus, setActionStatus] = useState("");
  const [uncertainRequest, setUncertainRequest] = useState<PendingRequest | null>(null);
  const pendingRequest = useRef<PendingRequest | null>(null);
  const selectedMonth = useRef(month);
  const workspaceVersion = useRef(0);
  const generationController = useRef<AbortController | null>(null);

  const loadSession = useCallback(async () => {
    try { const result = await readApi<SessionResponse>("/api/session"); setSession(result); setSessionError(""); }
    catch (error) { setSessionError(errorMessage(error, "서비스에 연결하지 못했습니다. 잠시 후 다시 불러오세요.")); }
  }, []);

  const refreshWorkspace = useCallback(async () => {
    const requestedMonth = workspaceBillingMonth(section, selectedMonth.current);
    if (requestedMonth !== selectedMonth.current) { selectedMonth.current = requestedMonth; setMonth(requestedMonth); }
    const version = ++workspaceVersion.current;
    setIsRefreshing(true);
    try {
      const result = await readApi<WorkspaceSnapshot>(`/api/workspace?${new URLSearchParams({ month: requestedMonth })}`);
      if (workspaceVersion.current === version) { setSnapshot(result); setWorkspaceError(""); setGeneration(current => current ? result.requests.find(request => request.id === current.id) ?? current : null); }
    } catch (error) {
      if (workspaceVersion.current !== version) return;
      setWorkspaceError(errorMessage(error, "사용 내역을 확인하지 못했습니다. 잠시 후 다시 불러오세요."));
      if (error instanceof ApiFailure && error.status === 401) await loadSession();
    } finally { if (workspaceVersion.current === version) setIsRefreshing(false); }
  }, [section, loadSession]);

  useEffect(() => {
    const controller = new AbortController();
    void readApi<SessionResponse>("/api/session", { signal: controller.signal }).then(result => {
      setSession(result); setSessionError("");
    }).catch(error => { if (!controller.signal.aborted) setSessionError(errorMessage(error, "서비스에 연결하지 못했습니다. 잠시 후 다시 불러오세요.")); });
    return () => controller.abort();
  }, []);
  const viewerId = session?.user?.id;
  useEffect(() => {
    if (!viewerId) return;
    const requestedMonth = workspaceBillingMonth(section, month);
    const version = ++workspaceVersion.current;
    const controller = new AbortController();
    void readApi<WorkspaceSnapshot>(`/api/workspace?${new URLSearchParams({ month: requestedMonth })}`, { signal: controller.signal }).then(result => {
      if (workspaceVersion.current === version) { selectedMonth.current = requestedMonth; setMonth(requestedMonth); setSnapshot(result); setWorkspaceError(""); setGeneration(current => current ? result.requests.find(request => request.id === current.id) ?? current : null); }
    }).catch(async error => {
      if (controller.signal.aborted || workspaceVersion.current !== version) return;
      setWorkspaceError(errorMessage(error, "사용 내역을 확인하지 못했습니다. 잠시 후 다시 불러오세요."));
      if (error instanceof ApiFailure && error.status === 401) await loadSession();
    }).finally(() => { if (workspaceVersion.current === version) setIsRefreshing(false); });
    return () => controller.abort();
  }, [viewerId, month, section, loadSession]);
  useEffect(() => {
    const restoreMonth = () => {
      const requested = new URLSearchParams(window.location.search).get("month");
      const restored = workspaceBillingMonth(section, requested && /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(requested) ? requested : currentBillingMonth());
      if (restored !== selectedMonth.current) { workspaceVersion.current++; selectedMonth.current = restored; setMonth(restored); setIsRefreshing(true); }
    };
    window.addEventListener("popstate", restoreMonth);
    return () => window.removeEventListener("popstate", restoreMonth);
  }, [section]);
  useEffect(() => {
    if (section !== "playground") return;
    const alignCurrentMonth = () => {
      if (document.visibilityState === "hidden") return;
      const activeMonth = workspaceBillingMonth(section, selectedMonth.current);
      if (activeMonth !== selectedMonth.current) { workspaceVersion.current++; selectedMonth.current = activeMonth; setMonth(activeMonth); setIsRefreshing(true); }
    };
    window.addEventListener("focus", alignCurrentMonth);
    document.addEventListener("visibilitychange", alignCurrentMonth);
    const timer = window.setInterval(alignCurrentMonth, 60_000);
    return () => { window.removeEventListener("focus", alignCurrentMonth); document.removeEventListener("visibilitychange", alignCurrentMonth); window.clearInterval(timer); };
  }, [section]);
  useEffect(() => () => { generationController.current?.abort(); workspaceVersion.current++; }, []);

  const login = async (accountId: string) => {
    setIsSigningIn(true); setSessionError("");
    try { await readApi("/api/session", jsonRequest({ accountId })); await loadSession(); }
    catch (error) { setSessionError(errorMessage(error, "체험을 시작하지 못했습니다. 다시 시도하세요.")); }
    finally { setIsSigningIn(false); }
  };

  const logout = async () => {
    setIsLoggingOut(true); setSessionError("");
    try {
      await readApi("/api/session", { method: "DELETE" });
      generationController.current?.abort(); workspaceVersion.current++;
      setSnapshot(null); setGeneration(null); setPrompt(""); pendingRequest.current = null; setUncertainRequest(null); setGenerationError(""); setWorkspaceError(""); setIsRefreshing(true);
      await loadSession();
    } catch (error) { setSessionError(errorMessage(error, "계정에서 나가지 못했습니다. 다시 시도하세요.")); }
    finally { setIsLoggingOut(false); }
  };

  const generate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isGenerating) return;
    const requestMonth = workspaceBillingMonth(section, selectedMonth.current);
    if (requestMonth !== selectedMonth.current) { workspaceVersion.current++; selectedMonth.current = requestMonth; setMonth(requestMonth); }
    const text = prompt.trim();
    if (!text || Array.from(text).length > 4000) { setGenerationError("메시지는 1자부터 4,000자까지 입력하세요."); document.getElementById("lumen-prompt")?.focus(); return; }
    const existing = pendingRequest.current;
    const candidate = existing?.prompt === text && existing.modelId === modelId ? existing : { requestId: crypto.randomUUID(), modelId, prompt: text };
    pendingRequest.current = candidate;
    setUncertainRequest(candidate);
    setIsGenerating(true); setGeneration(null); setGenerationError(""); setActionStatus("응답을 생성하고 있습니다.");
    const controller = new AbortController(); generationController.current = controller;
    try {
      let result = await readApi<GenerationView>("/api/generations", { ...jsonRequest(candidate), signal: controller.signal });
      setGeneration(result);
      for (let attempt = 0; result.state === "processing" && attempt < 30; attempt++) {
        await new Promise<void>((resolve, reject) => {
          const onAbort = () => { clearTimeout(timer); reject(new Error("aborted")); };
          const timer = setTimeout(() => { controller.signal.removeEventListener("abort", onAbort); resolve(); }, 1000);
          controller.signal.addEventListener("abort", onAbort, { once: true });
        });
        result = await readApi<GenerationView>(`/api/requests/${encodeURIComponent(candidate.requestId)}`, { signal: controller.signal });
        setGeneration(result);
      }
      if (result.state === "processing") throw new Error("processing");
      if (result.state === "failed") { setGenerationError("응답을 만들지 못했습니다. 같은 요청을 다시 확인하거나 메시지를 바꿔 보세요."); setActionStatus("응답 생성에 실패했습니다."); }
      else { pendingRequest.current = null; setUncertainRequest(null); setPrompt(""); setActionStatus("응답을 만들었습니다. 사용량을 확인하고 있습니다."); }
      await refreshWorkspace();
    } catch (error) {
      if (!controller.signal.aborted) {
        setGenerationError(errorMessage(error, "응답 결과를 아직 확인하지 못했습니다. 같은 요청 다시 확인을 누르면 중복 생성 없이 이어서 확인합니다."));
        setActionStatus("요청 결과를 확인하지 못했습니다.");
        if (error instanceof ApiFailure && error.status === 401) await loadSession();
      }
    } finally { if (generationController.current === controller) { generationController.current = null; setIsGenerating(false); } }
  };

  const retryMetering = async () => {
    setIsRetrying(true); setActionStatus("");
    try {
      const result = await readApi<{ delivered: number; pending: number; failed: number }>("/api/metering/retry", jsonRequest({}));
      setActionStatus(result.pending + result.failed > 0 ? `사용량 ${result.pending + result.failed}건을 아직 확인하고 있습니다. 잠시 후 다시 시도하세요.` : "사용량 반영을 확인했습니다.");
      await refreshWorkspace();
      if (generation) { const result = await readApi<GenerationView>(`/api/requests/${encodeURIComponent(generation.id)}`); setGeneration(result); }
    } catch (error) { setActionStatus(errorMessage(error, "사용량을 다시 보내지 못했습니다. 대화와 전송 대기 기록은 보존됩니다.")); }
    finally { setIsRetrying(false); }
  };

  const isCurrentSnapshot = snapshot !== null && snapshot.user.id === session?.user?.id && snapshot.month === month;
  const selectedTitle = NAV_ITEMS.find(item => item.id === section)!.label;
  const hasUncertainRequest = uncertainRequest !== null && uncertainRequest.prompt === prompt.trim() && uncertainRequest.modelId === modelId;

  const changeMonth = (value: string) => {
    if (value === month) { void refreshWorkspace(); return; }
    setIsRefreshing(true);
    workspaceVersion.current++;
    selectedMonth.current = value;
    setMonth(value);
    const url = new URL(window.location.href);
    url.searchParams.set("month", value);
    window.history.pushState(null, "", `${url.pathname}${url.search}${url.hash}`);
  };

  return <ConfigProvider theme={LUMEN_THEME}>
    {!session?.user ? <LoginScreen session={session} error={sessionError} isBusy={isSigningIn} onLogin={id => void login(id)} onReload={() => void loadSession()} /> : <div className="application">
      <header className="app-header"><Link href="/" className="wordmark" aria-label="Lumen 홈">Lumen</Link><nav aria-label="주요 메뉴">{NAV_ITEMS.map(item => <Link href={item.id === "playground" ? item.href : `${item.href}?month=${encodeURIComponent(month)}`} key={item.id} aria-current={item.id === section ? "page" : undefined}>{item.label}</Link>)}</nav><div className="account-controls"><div><strong>{session.user.name}</strong><span>{session.user.email}</span></div><Button onClick={() => void logout()} loading={isLoggingOut} icon={<LogoutOutlined aria-hidden="true" />} aria-label="계정 나가기">나가기</Button></div></header>
      <main id="main-content" className="workspace" aria-busy={isRefreshing && !isCurrentSnapshot}>
        {sessionError && <Notice>{sessionError}</Notice>}
        {section !== "playground" && <div className="page-heading"><div><h1>{selectedTitle}</h1><p>{section === "usage" ? "모델별 사용량과 적용 요금을 확인하세요." : section === "billing" ? "예정 요금과 확정된 청구서, 납부 상태를 구분해 확인하세요." : "내가 보낸 메시지와 응답을 다시 확인하세요."}</p></div><MonthFilter key={month} value={month} onApply={changeMonth} isBusy={isRefreshing} /></div>}
        {workspaceError && <Notice title="작업 공간을 불러오지 못했습니다" onRetry={() => void refreshWorkspace()} isBusy={isRefreshing}>{workspaceError}</Notice>}
        {!isCurrentSnapshot ? <div className="workspace-loading" role="status"><LoadingOutlined spin aria-hidden="true" /><span>{isRefreshing ? "사용 내역을 불러오고 있습니다." : "사용 내역을 다시 불러와 주세요."}</span>{!isRefreshing && <Button onClick={() => void refreshWorkspace()}>다시 불러오기</Button>}</div> : <>
          {section === "playground" ? <Playground snapshot={snapshot} generation={generation} prompt={prompt} modelId={modelId} error={generationError} isGenerating={isGenerating} isRetrying={isRetrying} isRefreshing={isRefreshing} hasUncertainRequest={hasUncertainRequest} onPromptChange={value => { setPrompt(value); setGenerationError(""); }} onModelChange={value => { setModelId(value); setGenerationError(""); }} onSubmit={event => void generate(event)} onRetryMetering={() => void retryMetering()} onRefresh={() => void refreshWorkspace()} onSelectRequest={request => { setGeneration(request); setPrompt(request.prompt); setModelId(request.modelId); setGenerationError(""); const pending = request.state === "processing" ? { requestId: request.id, modelId: request.modelId, prompt: request.prompt } : null; pendingRequest.current = pending; setUncertainRequest(pending); }} /> : section === "usage" ? <UsageScreen snapshot={snapshot} isRetrying={isRetrying} onRetryMetering={() => void retryMetering()} /> : section === "billing" ? <BillingScreen snapshot={snapshot} /> : <HistoryScreen snapshot={snapshot} />}
          {section !== "playground" && <div className="refresh-control"><span>내 계정의 {month} 자료입니다.</span><Button onClick={() => void refreshWorkspace()} loading={isRefreshing} icon={<ReloadOutlined aria-hidden="true" />}>새로 불러오기</Button></div>}
        </>}
        <p className="action-status" role="status" aria-live="polite">{actionStatus}</p>
      </main><footer className="app-footer"><span>Lumen 고객 서비스 예제</span><span>모델 응답은 로컬 모형, 사용량과 청구는 실제 연동 결과입니다.</span></footer>
    </div>}
  </ConfigProvider>;
}
