"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { App, Button, Drawer } from "antd";
import { MenuOutlined, ReloadOutlined, SwapOutlined } from "@ant-design/icons";
import { Suspense, useEffect, useLayoutEffect, useRef, useState, useTransition } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname, useRouter } from "next/navigation";
import { LogoutButton } from "@/components/auth/SessionControls";
import { NavMenu } from "@/components/shell/NavMenu";
import { BrandMark } from "@/components/shell/BrandMark";
import { StatusTag } from "@/components/screen/StatusTag";
import { workspaceHref } from "@/lib/workspace-href";
import { TenantAccessProvider } from "@/components/auth/TenantAccess";
import { hasScope, ORGANIZATION_ROLE_LABELS, scopeSummary, type OrganizationRole } from "@/lib/auth/permissions";

import { EnvironmentSwitch } from "@/components/workspace/EnvironmentSwitch";
import { ENVIRONMENT_LABELS, environmentNoticeClass, LIVE_ENVIRONMENT_PATH, readEnvironment, type WorkspaceEnvironment } from "@/lib/environment";

const ENVIRONMENT_NOTICES: Record<WorkspaceEnvironment, string> = {
  test: "에서는 고객부터 수납까지 실제 환경과 따로 관리해요. 사용자와 업무 권한은 두 환경에서 같아요.",
  live: "에서는 고객부터 수납까지 테스트 환경과 따로 관리해요. 사용자와 업무 권한은 두 환경에서 같아요.",
  legacy: "는 환경을 나누기 전에 저장한 것이라 테스트 환경이나 실제 환경과 따로 보관해요.",
};

type Props = { organizationId: string; organizationLabel: string; canSwitchOrganization: boolean; userLabel: string; isOperator: boolean; organizationRole?: OrganizationRole | null; scopes: string[]; showLegacyEnvironment: boolean; isLiveEnvironmentOpen: boolean; children: React.ReactNode };

function ShellContent({ organizationId, organizationLabel, canSwitchOrganization, userLabel, isOperator, organizationRole, scopes, showLegacyEnvironment, isLiveEnvironmentOpen, children }: Props) {
  const router = useRouter();
  const [isNavOpen, setIsNavOpen] = useState(false);
  const headerRef = useRef<HTMLElement>(null);
  const pathname = usePathname();
  const search = useWorkspaceSearch().toString();
  const environment = readEnvironment(new URLSearchParams(search).get("environment"));
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
  const canRead = hasScope(scopes, "read");
  const canManage = hasScope(scopes, "admin");
  const scopeLabel = `${scopeSummary(scopes)}${!canManage && canRead && !hasScope(scopes, "ingest") ? " 전용" : ""}`;
  const { message } = App.useApp();
  const [isRefreshing, startRefresh] = useTransition();
  const wasRefreshing = useRef(false);
  useEffect(() => {
    if (wasRefreshing.current && !isRefreshing) message.success("권한을 다시 조회했어요.");
    wasRefreshing.current = isRefreshing;
  }, [isRefreshing, message]);
  // 폰에서는 헤더에 업무 환경만 남기고 이 묶음을 메뉴 서랍에서 보인다.
  const accountControls = <>{isOperator && <Link href="/admin">플랫폼 운영</Link>}<span className="workspace-user-name">{userLabel}{organizationRole ? ` / ${ORGANIZATION_ROLE_LABELS[organizationRole]}` : ""}</span><span className="workspace-scope"><StatusTag>{scopeLabel}</StatusTag></span><Button type="text" size="small" icon={<ReloadOutlined aria-hidden="true" />} loading={isRefreshing} onClick={() => startRefresh(() => router.refresh())} title="바뀐 권한을 다시 불러와요">권한 새로 조회</Button><LogoutButton type="text" size="small" /></>;
  return <div className="workspace-shell">
    <header ref={headerRef} className="workspace-header">
      <div className="workspace-brand">
        <Button className="mobile-menu" type="text" icon={<MenuOutlined />} aria-label="메뉴 열기" aria-expanded={isNavOpen} onClick={() => setIsNavOpen(true)} />
        <Link href={workspaceHref("/overview", search, { inheritFilters: false })} className="wordmark"><BrandMark />MeterEngine</Link>
        <span className="workspace-divider" />
        {canSwitchOrganization
          ? <Link href="/organizations" className="organization-link" title={organizationLabel}><span className="organization-name">{organizationLabel}</span><SwapOutlined aria-hidden="true" /></Link>
          : <span className="organization-link" title={organizationLabel}><span className="organization-name">{organizationLabel}</span></span>}
      </div>
      <div className="workspace-user"><EnvironmentSwitch showLegacy={showLegacyEnvironment} isLiveEnvironmentOpen={isLiveEnvironmentOpen} /><div className="workspace-account">{accountControls}</div></div>
    </header>
    <aside className="workspace-sidebar"><NavMenu /></aside>
    <main id="main-content" className="workspace-main">
      <div className="workspace-content" key={`${organizationId}:${environment}`}><p className={environmentNoticeClass(environment)} role="status"><strong>{ENVIRONMENT_LABELS[environment]}</strong>{ENVIRONMENT_NOTICES[environment]}{!isLiveEnvironmentOpen && pathname !== LIVE_ENVIRONMENT_PATH && <> 실제 환경은 이용 신청이 승인되면 쓸 수 있어요. <Link href={LIVE_ENVIRONMENT_PATH}>실제 환경 이용 신청</Link></>}</p>{canRead ? children : <section className="workspace-panel" aria-labelledby="tenant-permission-title"><h1 id="tenant-permission-title">조회 권한이 없어요</h1><p>{hasScope(scopes, "ingest") ? "이 계정은 서버 연동으로 고객을 등록하고 사용량 이벤트를 보낼 수 있어요. 콘솔에서 고객과 청구 내역을 보려면 대표관리자에게 조회 권한을 요청하세요." : "고객과 청구 내역을 볼 권한이 없어요. 대표관리자에게 권한을 요청하거나 다른 조직을 선택하세요."}</p><Link href="/organizations">다른 조직 선택</Link></section>}</div>
    </main>
    <Drawer title={organizationLabel} open={isNavOpen} onClose={() => setIsNavOpen(false)} placement="left" size={280}><NavMenu onNavigate={() => setIsNavOpen(false)} /><div className="drawer-account">{accountControls}</div></Drawer>
  </div>;
}

export function ConsoleShell(props: Props) {
  return <TenantAccessProvider organizationId={props.organizationId} scopes={props.scopes} organizationRole={props.organizationRole} isLiveEnvironmentOpen={props.isLiveEnvironmentOpen}><Suspense fallback={<div className="workspace-content" role="status">불러오는 중이에요.</div>}><ShellContent {...props} /></Suspense></TenantAccessProvider>;
}
