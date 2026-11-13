"use client";

import { LoadingOutlined, LogoutOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, ConfigProvider } from "antd";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { COMPANY } from "@/lib/catalog";
import type { SessionResponse, WorkspaceSnapshot } from "@/lib/contracts";
import { ApiPlayground } from "./ApiPlayground";
import { BatchScreen } from "./BatchScreen";
import { currentBillingMonth, hasMonthFilter, workspaceBillingMonth } from "./billing-month";
import type { WorkspaceSection } from "./billing-month";
import { BillingScreen } from "./BillingScreen";
import { ChatScreen } from "./ChatScreen";
import { ApiFailure, errorMessage, jsonRequest, readApi } from "./client-api";
import { Notice, SyncStatus } from "./Feedback";
import { MONEY } from "./format";
import { HistoryScreen } from "./HistoryScreen";
import { LoginScreen } from "./LoginScreen";
import { UsageScreen } from "./UsageScreen";

type NavItem = { id: WorkspaceSection; label: string; href: string };

const PRODUCT_NAV: NavItem[] = [
  { id: "chat", label: "대화", href: "/" },
  { id: "api", label: "API", href: "/playground" },
  { id: "batch", label: "일괄 처리", href: "/batch" },
];
const ACCOUNT_NAV: NavItem[] = [
  { id: "usage", label: "사용량", href: "/usage" },
  { id: "billing", label: "청구 내역", href: "/billing" },
  { id: "history", label: "요청 기록", href: "/history" },
];
const HEADINGS: Record<WorkspaceSection, { title: string; description: string }> = {
  chat: { title: "대화", description: "" },
  api: { title: "API 플레이그라운드", description: "모델과 메시지를 넣어 요청을 보내 보세요. 요청 본문과 응답 JSON, 토큰 사용량을 함께 보여 드려요." },
  batch: { title: "일괄 처리", description: "여러 작업을 한 번에 맡기고 건마다 결과를 받아요." },
  usage: { title: "사용량", description: "모델과 용도별 토큰 사용량과 적용 단가를 확인하세요." },
  billing: { title: "청구 내역", description: "청구 예정액과 확정된 청구서, 납부 상태를 확인하세요." },
  history: { title: "요청 기록", description: "대화, API, 일괄 처리로 보낸 요청과 응답을 다시 볼 수 있어요." },
};
const MONTH_PATTERN = /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/;
const THEME = {
  token: { colorPrimary: "#3b33c4", colorText: "#15192b", colorTextSecondary: "#545b73", colorTextPlaceholder: "#5f667e", colorBgContainer: "#ffffff", colorBorder: "#8a90a6", borderRadius: 10, controlHeight: 44, fontSize: 15, fontFamily: 'var(--font-pretendard), "Apple SD Gothic Neo", "Malgun Gothic", sans-serif' },
};

function MonthFilter({ value, onApply, isBusy }: { value: string; onApply: (value: string) => void; isBusy: boolean }) {
  const [draft, setDraft] = useState(value);
  const [error, setError] = useState("");
  return <form className="month-filter" onSubmit={event => {
    event.preventDefault();
    if (!MONTH_PATTERN.test(draft)) { setError("조회할 월을 골라 주세요."); return; }
    setError(""); onApply(draft);
  }}><label htmlFor="month-input">조회 월</label><div><input id="month-input" name="month" type="month" value={draft} onChange={event => setDraft(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={error ? "month-error" : "month-hint"} required /><Button htmlType="submit" loading={isBusy}>보기</Button></div>{error ? <span id="month-error" className="field-error">{error}</span> : <span id="month-hint" className="field-help">한국 시간 기준</span>}</form>;
}

// 메뉴를 옮기면 화면이 다시 마운트되므로 마지막 세션으로 먼저 그리고 다시 확인한다.
let lastSession: SessionResponse | null = null;

export function OpenAbApplication({ section, initialMonth }: { section: WorkspaceSection; initialMonth?: string }) {
  const [session, setSession] = useState<SessionResponse | null>(() => lastSession);
  const [sessionError, setSessionError] = useState("");
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [snapshot, setSnapshot] = useState<WorkspaceSnapshot | null>(null);
  const [workspaceError, setWorkspaceError] = useState("");
  const [isRefreshing, setIsRefreshing] = useState(true);
  const [month, setMonth] = useState(() => workspaceBillingMonth(section, initialMonth ?? currentBillingMonth()));
  const [isRetrying, setIsRetrying] = useState(false);
  const [actionStatus, setActionStatus] = useState("");
  const selectedMonth = useRef(month);
  const workspaceVersion = useRef(0);

  const loadSession = useCallback(async () => {
    try { const result = await readApi<SessionResponse>("/api/session"); lastSession = result; setSession(result); setSessionError(""); }
    catch (error) { setSessionError(errorMessage(error, "서비스에 연결하지 못했어요. 잠시 뒤 다시 불러와 주세요.")); }
  }, []);

  const refreshWorkspace = useCallback(async () => {
    const requestedMonth = workspaceBillingMonth(section, selectedMonth.current);
    if (requestedMonth !== selectedMonth.current) { selectedMonth.current = requestedMonth; setMonth(requestedMonth); }
    const version = ++workspaceVersion.current;
    setIsRefreshing(true);
    try {
      const result = await readApi<WorkspaceSnapshot>(`/api/workspace?${new URLSearchParams({ month: requestedMonth })}`);
      if (workspaceVersion.current === version) { setSnapshot(result); setWorkspaceError(""); }
    } catch (error) {
      if (workspaceVersion.current !== version) return;
      setWorkspaceError(errorMessage(error, "사용 내역을 불러오지 못했어요. 잠시 뒤 다시 불러와 주세요."));
      if (error instanceof ApiFailure && error.status === 401) await loadSession();
    } finally { if (workspaceVersion.current === version) setIsRefreshing(false); }
  }, [section, loadSession]);

  useEffect(() => {
    const controller = new AbortController();
    void readApi<SessionResponse>("/api/session", { signal: controller.signal }).then(result => {
      lastSession = result; setSession(result); setSessionError("");
    }).catch(error => { if (!controller.signal.aborted) setSessionError(errorMessage(error, "서비스에 연결하지 못했어요. 잠시 뒤 다시 불러와 주세요.")); });
    return () => controller.abort();
  }, []);
  const viewerId = session?.user?.id;
  useEffect(() => {
    if (!viewerId) return;
    const requestedMonth = workspaceBillingMonth(section, month);
    const version = ++workspaceVersion.current;
    const controller = new AbortController();
    void readApi<WorkspaceSnapshot>(`/api/workspace?${new URLSearchParams({ month: requestedMonth })}`, { signal: controller.signal }).then(result => {
      if (workspaceVersion.current === version) { selectedMonth.current = requestedMonth; setMonth(requestedMonth); setSnapshot(result); setWorkspaceError(""); }
    }).catch(async error => {
      if (controller.signal.aborted || workspaceVersion.current !== version) return;
      setWorkspaceError(errorMessage(error, "사용 내역을 불러오지 못했어요. 잠시 뒤 다시 불러와 주세요."));
      if (error instanceof ApiFailure && error.status === 401) await loadSession();
    }).finally(() => { if (workspaceVersion.current === version) setIsRefreshing(false); });
    return () => controller.abort();
  }, [viewerId, month, section, loadSession]);
  useEffect(() => {
    const restoreMonth = () => {
      const requested = new URLSearchParams(window.location.search).get("month");
      const restored = workspaceBillingMonth(section, requested && MONTH_PATTERN.test(requested) ? requested : currentBillingMonth());
      if (restored !== selectedMonth.current) { workspaceVersion.current++; selectedMonth.current = restored; setMonth(restored); setIsRefreshing(true); }
    };
    window.addEventListener("popstate", restoreMonth);
    return () => window.removeEventListener("popstate", restoreMonth);
  }, [section]);
  useEffect(() => {
    if (hasMonthFilter(section)) return;
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
  useEffect(() => () => { workspaceVersion.current++; }, []);

  const login = async (accountId: string) => {
    setIsSigningIn(true); setSessionError("");
    try { await readApi("/api/session", jsonRequest({ accountId })); await loadSession(); }
    catch (error) { setSessionError(errorMessage(error, "체험을 시작하지 못했어요. 다시 시도해 주세요.")); }
    finally { setIsSigningIn(false); }
  };

  const logout = async () => {
    setIsLoggingOut(true); setSessionError("");
    try {
      await readApi("/api/session", { method: "DELETE" });
      workspaceVersion.current++;
      setSnapshot(null); setWorkspaceError(""); setIsRefreshing(true);
      await loadSession();
    } catch (error) { setSessionError(errorMessage(error, "계정에서 나가지 못했어요. 다시 시도해 주세요.")); }
    finally { setIsLoggingOut(false); }
  };

  const retryMetering = async () => {
    setIsRetrying(true); setActionStatus("");
    try {
      const result = await readApi<{ delivered: number; pending: number; failed: number }>("/api/metering/retry", jsonRequest({}));
      setActionStatus(result.pending + result.failed > 0 ? `사용량 ${result.pending + result.failed}건을 아직 기록하지 못했어요. 잠시 뒤 다시 시도해 주세요.` : "사용량을 모두 기록했어요.");
      await refreshWorkspace();
    } catch (error) { setActionStatus(errorMessage(error, "사용량을 다시 보내지 못했어요. 응답과 보낼 사용량은 그대로 보관해요.")); }
    finally { setIsRetrying(false); }
  };

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

  const onSessionLost = useCallback(() => { void loadSession(); }, [loadSession]);
  const onUsageChanged = useCallback(() => { void refreshWorkspace(); }, [refreshWorkspace]);
  const isCurrentSnapshot = snapshot !== null && snapshot.user.id === session?.user?.id && snapshot.month === month;
  const heading = HEADINGS[section];
  const isModelScreen = !hasMonthFilter(section);
  const draftTotal = snapshot && snapshot.month === currentBillingMonth() ? snapshot.billing.draft?.totalAmount : undefined;
  const navLink = (item: NavItem) => <Link key={item.id} href={hasMonthFilter(item.id) ? `${item.href}?month=${encodeURIComponent(hasMonthFilter(section) ? month : currentBillingMonth())}` : item.href} aria-current={item.id === section ? "page" : undefined}>{item.label}</Link>;

  return <ConfigProvider theme={THEME}>{!session?.user ? <LoginScreen session={session} error={sessionError} isBusy={isSigningIn} onLogin={id => void login(id)} onReload={() => void loadSession()} /> : <div className={`application application-${section}`}>
      <header className="app-header">
        <Link href="/" className="wordmark">{COMPANY.name}</Link>
        <nav aria-label="주요 메뉴"><div className="nav-group">{PRODUCT_NAV.map(navLink)}</div><div className="nav-group nav-group-account">{ACCOUNT_NAV.map(navLink)}</div></nav>
        <div className="account-controls">
          {draftTotal !== undefined && <Link href={`/billing?month=${currentBillingMonth()}`} className="header-bill">이번 달 예정액 <strong>{MONEY.format(draftTotal)}</strong></Link>}
          <div className="account-name"><strong>{session.user.name}</strong><span>{session.user.email}</span></div>
          <Button onClick={() => void logout()} loading={isLoggingOut} icon={<LogoutOutlined aria-hidden="true" />}>나가기</Button>
        </div>
      </header>
      <main id="main-content" className="workspace" aria-busy={isRefreshing && !isCurrentSnapshot}>
        {sessionError && <Notice>{sessionError}</Notice>}
        {section !== "chat" && <div className="page-heading"><div><h1>{heading.title}</h1><p>{heading.description}</p></div>{hasMonthFilter(section) && <MonthFilter key={month} value={month} onApply={changeMonth} isBusy={isRefreshing} />}</div>}
        {workspaceError && <Notice title="사용 내역을 불러오지 못했어요" onRetry={() => void refreshWorkspace()} isBusy={isRefreshing}>{workspaceError}</Notice>}
        {!isCurrentSnapshot ? <div className="workspace-loading" role="status">{isRefreshing ? <><LoadingOutlined spin aria-hidden="true" /><span>사용 내역을 불러오고 있어요.</span></> : <><span>사용 내역을 다시 불러와 주세요.</span><Button onClick={() => void refreshWorkspace()}>다시 불러오기</Button></>}</div> : <>
          {isModelScreen && (snapshot.usage.state === "unavailable" || snapshot.billing.state === "unavailable") && <Notice title="사용량과 청구 정보 일부를 불러오지 못했어요" onRetry={() => void refreshWorkspace()} isBusy={isRefreshing}>
            {snapshot.usage.state === "unavailable" && <span className="notice-detail">사용량: {snapshot.usage.error || "잠시 뒤 다시 불러와 주세요."}</span>}
            {snapshot.billing.state === "unavailable" && <span className="notice-detail">청구: {snapshot.billing.error || "잠시 뒤 다시 불러와 주세요."}</span>}
            <span className="notice-detail">대화와 요청 기록은 그대로 있어요. 단가와 청구 예정액은 다시 불러온 뒤 보여 드려요.</span>
          </Notice>}
          {isModelScreen && snapshot.stats.pendingMetering + snapshot.stats.failedMetering > 0 && <SyncStatus snapshot={snapshot} isBusy={isRetrying} onRetry={() => void retryMetering()} />}
          {section === "chat" ? <ChatScreen snapshot={snapshot} onUsageChanged={onUsageChanged} onSessionLost={onSessionLost} />
            : section === "api" ? <ApiPlayground snapshot={snapshot} onUsageChanged={onUsageChanged} onSessionLost={onSessionLost} />
            : section === "batch" ? <BatchScreen snapshot={snapshot} onUsageChanged={onUsageChanged} onSessionLost={onSessionLost} />
            : section === "usage" ? <UsageScreen snapshot={snapshot} isRetrying={isRetrying} onRetryMetering={() => void retryMetering()} />
            : section === "billing" ? <BillingScreen snapshot={snapshot} /> : <HistoryScreen snapshot={snapshot} />}
          {!isModelScreen && <div className="refresh-control"><span>{session.user.name}의 {month} 자료예요.</span><Button onClick={() => void refreshWorkspace()} loading={isRefreshing} icon={<ReloadOutlined aria-hidden="true" />}>새로 불러오기</Button></div>}
        </>}
        <p className={section === "chat" ? "screen-reader-only" : "action-status"} role="status" aria-live="polite">{actionStatus}</p>
      </main>
    </div>}</ConfigProvider>;
}
