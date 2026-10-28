"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { App, Button, Drawer } from "antd";
import { DownOutlined, MenuOutlined, ReloadOutlined } from "@ant-design/icons";
import { Suspense, useEffect, useLayoutEffect, useRef, useState, useTransition } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname, useRouter } from "next/navigation";
import { LogoutButton } from "@/components/auth/SessionControls";
import { NavMenu } from "@/components/shell/NavMenu";
import { GuideModeToggle, useGuideMode } from "@/components/shell/GuideMode";
import { workspaceHref } from "@/lib/workspace-href";
import { TenantAccessProvider } from "@/components/auth/TenantAccess";
import { hasScope, scopeSummary, type OrganizationRole } from "@/lib/auth/permissions";


const WORKFLOW = [
  { href: "/usage", label: "사용량 확인", detail: "고객별 수량과 정정 이벤트를 확인하세요." },
  { href: "/billing", label: "청구 검토", detail: "예정액과 과금에서 빠진 사용량을 검토한 뒤 확정하세요." },
];

type Props = { organizationId: string; organizationLabel: string; userLabel: string; isOperator: boolean; organizationRole?: OrganizationRole | null; scopes: string[]; children: React.ReactNode };

function ShellContent({ organizationId, organizationLabel, userLabel, isOperator, organizationRole, scopes, children }: Props) {
  const router = useRouter();
  const [isNavOpen, setIsNavOpen] = useState(false);
  const headerRef = useRef<HTMLElement>(null);
  const pathname = usePathname();
  const search = useWorkspaceSearch().toString();
  useLayoutEffect(() => {
    const current = new URL(window.location.href);
    if (current.searchParams.get("organization_id")) return;
    current.searchParams.set("organization_id", organizationId);
    current.searchParams.delete("_rsc");
    window.history.replaceState(null, "", current.pathname + current.search + current.hash);
  }, [organizationId, pathname, search]);
  useLayoutEffect(() => {
    const header = headerRef.current;
    if (!header) return;
    const root = document.documentElement;
    const observer = new ResizeObserver(() => root.style.setProperty("--workspace-header-height", `${Math.round(header.getBoundingClientRect().height)}px`));
    observer.observe(header);
    return () => { observer.disconnect(); root.style.removeProperty("--workspace-header-height"); };
  }, []);
  const { isGuideMode } = useGuideMode();
  const canRead = hasScope(scopes, "read");
  const canManage = hasScope(scopes, "admin");
  const scopeLabel = `${scopeSummary(scopes)}${!canManage && canRead && !hasScope(scopes, "ingest") ? " 전용" : ""}`;
  const { message } = App.useApp();
  const [isRefreshing, startRefresh] = useTransition();
  const wasRefreshing = useRef(false);
  useEffect(() => {
    if (wasRefreshing.current && !isRefreshing) message.success("권한을 다시 조회했습니다.");
    wasRefreshing.current = isRefreshing;
  }, [isRefreshing, message]);
  return <div className="workspace-shell">
    <header ref={headerRef} className="workspace-header">
      <div className="workspace-brand">
        <Button className="mobile-menu" type="text" icon={<MenuOutlined />} aria-label="업무 메뉴 열기" aria-expanded={isNavOpen} onClick={() => setIsNavOpen(true)} />
        <Link href={workspaceHref("/customers", search, { inheritFilters: false })} className="wordmark"><span className="meter-mark" aria-hidden="true"><i /><i /><i /></span>MeterEngine</Link>
        <span className="workspace-divider" />
        <Link href="/organizations" className="organization-link" title={organizationLabel}>{organizationLabel}<DownOutlined aria-hidden="true" /></Link>
      </div>
      <div className="workspace-user">{canManage && <GuideModeToggle />}{isOperator && <Link href="/admin/organizations">플랫폼 운영</Link>}<span className="workspace-user-name">{userLabel}{organizationRole ? ` / ${organizationRole === "owner" ? "대표관리자" : organizationRole === "admin" ? "관리자" : "사용자"}` : ""}</span><span className="workspace-scope">{scopeLabel}</span><Button type="text" size="small" icon={<ReloadOutlined aria-hidden="true" />} loading={isRefreshing} onClick={() => startRefresh(() => router.refresh())} title="현재 도입사의 권한을 다시 조회합니다">권한 새로 조회</Button><LogoutButton /></div>
    </header>
    <aside className="workspace-sidebar"><div className="workspace-sidebar-label">도입사 업무</div><NavMenu /><div className="workspace-sidebar-note">{canRead ? <Link href={workspaceHref("/integrations", search)}>서버 연동 설정</Link> : <Link href="/organizations">도입사 선택</Link>}<p>사람의 관리 권한과 API 키의 권한은 별도로 관리합니다.</p></div></aside>
    <main id="main-content" className="workspace-main">
      {isGuideMode && canManage && <section className="workspace-guide" aria-label="시연 안내"><div className="workspace-guide-heading"><div><h2>한 고객의 사용량에서 수납까지</h2><p>현재 업무 환경의 데이터를 사용합니다. 시연 안내는 데이터와 API 키, 결제 환경을 바꾸지 않습니다.</p></div><GuideModeToggle /></div><nav className="workflow-nav" aria-label="시연 진행 순서">{WORKFLOW.map((step, index) => <Link key={step.href} href={workspaceHref(step.href, search)} aria-current={pathname === step.href ? "page" : undefined}><span className="workflow-index">{index + 1}</span><span><strong>{step.label}</strong><small>{step.detail}</small></span></Link>)}</nav><details><summary>시연 준비 확인</summary><ul><li>고객을 등록하고 외부 고객 ID를 연결합니다.</li><li>미터와 적용 단가를 등록하고 read/ingest 권한의 서버 연동 키를 준비합니다.</li><li>현재 환경의 토스 키를 확인하고 고객의 동의를 받아 카드를 등록합니다.</li><li>사용량 전송, 청구 확정, 수납 결과와 통보 수신을 같은 고객과 기간으로 확인합니다.</li></ul></details></section>}
      <div className="workspace-content" key={organizationId}>{canRead ? children : <section className="workspace-panel" aria-labelledby="tenant-permission-title"><h1 id="tenant-permission-title">업무 조회 권한이 없습니다</h1><p>{hasScope(scopes, "ingest") ? "현재 계정은 서버 연동으로 고객을 등록하고 사용량 이벤트를 수집할 수 있습니다. 콘솔에서 고객과 청구 내역을 보려면 대표관리자에게 조회 권한을 요청하세요." : "현재 도입사의 업무를 조회할 권한이 없습니다. 대표관리자에게 권한을 요청하거나 다른 도입사를 선택하세요."}</p><Link href="/organizations">다른 도입사 선택</Link></section>}</div>
    </main>
    <Drawer title={organizationLabel} open={isNavOpen} onClose={() => setIsNavOpen(false)} placement="left" size={280}><NavMenu onNavigate={() => setIsNavOpen(false)} /></Drawer>
  </div>;
}

export function ConsoleShell(props: Props) {
  return <TenantAccessProvider organizationId={props.organizationId} scopes={props.scopes} organizationRole={props.organizationRole}><Suspense fallback={<div className="workspace-content" role="status">업무 화면을 준비하고 있습니다.</div>}><ShellContent {...props} /></Suspense></TenantAccessProvider>;
}
