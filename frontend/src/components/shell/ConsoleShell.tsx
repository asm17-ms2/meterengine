"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { Button, Drawer } from "antd";
import { MenuOutlined, SwapOutlined } from "@ant-design/icons";
import { Suspense, useLayoutEffect, useRef, useState } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname } from "next/navigation";
import { LogoutButton } from "@/components/auth/SessionControls";
import { NavMenu } from "@/components/shell/NavMenu";
import { BrandMark } from "@/components/shell/BrandMark";
import { StatusTag } from "@/components/screen/StatusTag";
import { workspaceHref } from "@/lib/workspace-href";
import { TenantAccessProvider } from "@/components/auth/TenantAccess";
import { hasScope, ORGANIZATION_ROLE_LABELS, scopeSummary, type OrganizationRole } from "@/lib/auth/permissions";

import { EnvironmentSwitch, useEnvironmentSwitch } from "@/components/workspace/EnvironmentSwitch";
import { ENVIRONMENT_BAND_MESSAGES, environmentBandClass, nextEnvironment, readEnvironment } from "@/lib/environment";

function EnvironmentBand({ isLiveEnvironmentOpen }: { isLiveEnvironmentOpen: boolean }) {
  const { environment, confirmSwitch } = useEnvironmentSwitch(isLiveEnvironmentOpen);
  return <div className={environmentBandClass(environment)} role="region" aria-label="현재 업무 환경"><p>{ENVIRONMENT_BAND_MESSAGES[environment]}</p><Button size="small" onClick={() => confirmSwitch(nextEnvironment(environment))}>환경 바꾸기</Button></div>;
}

type Props = { organizationId: string; organizationLabel: string; canSwitchOrganization: boolean; userLabel: string; isOperator: boolean; organizationRole?: OrganizationRole | null; scopes: string[]; showLegacyEnvironment: boolean; isLiveEnvironmentOpen: boolean; children: React.ReactNode };

function ShellContent({ organizationId, organizationLabel, canSwitchOrganization, userLabel, isOperator, organizationRole, scopes, showLegacyEnvironment, isLiveEnvironmentOpen, children }: Props) {
  const [isNavOpen, setIsNavOpen] = useState(false);
  const headerRef = useRef<HTMLDivElement>(null);
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
  // 폰에서는 헤더에 업무 환경만 남기고 이 묶음을 메뉴 서랍에서 보인다.
  const accountControls = <>{isOperator && <Link href="/admin">플랫폼 운영</Link>}<span className="workspace-user-name">{userLabel}{organizationRole ? ` / ${ORGANIZATION_ROLE_LABELS[organizationRole]}` : ""}</span><span className="workspace-scope"><StatusTag>{scopeLabel}</StatusTag></span><LogoutButton type="text" size="small" /></>;
  return <div className="workspace-shell">
    <div ref={headerRef} className="workspace-top">
    <EnvironmentBand isLiveEnvironmentOpen={isLiveEnvironmentOpen} />
    <header className="workspace-header">
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
    </div>
    <aside className="workspace-sidebar"><NavMenu /></aside>
    <main id="main-content" className="workspace-main">
      <div className="workspace-content" key={`${organizationId}:${environment}`}>{canRead ? children : <section className="workspace-panel" aria-labelledby="tenant-permission-title"><h1 id="tenant-permission-title">조회 권한이 없어요</h1><p>{hasScope(scopes, "ingest") ? "이 계정은 서버 연동으로 고객을 등록하고 사용량 이벤트를 보낼 수 있어요. 콘솔에서 고객과 청구 내역을 보려면 대표관리자에게 조회 권한을 요청하세요." : "고객과 청구 내역을 볼 권한이 없어요. 대표관리자에게 권한을 요청하거나 다른 조직을 선택하세요."} 대표관리자가 권한을 바꾸면 새로고침하세요.</p><Link href="/organizations">다른 조직 선택</Link></section>}</div>
    </main>
    <Drawer title={organizationLabel} open={isNavOpen} onClose={() => setIsNavOpen(false)} placement="left" size={280}><NavMenu onNavigate={() => setIsNavOpen(false)} /><div className="drawer-account">{accountControls}</div></Drawer>
  </div>;
}

export function ConsoleShell(props: Props) {
  return <TenantAccessProvider organizationId={props.organizationId} scopes={props.scopes} organizationRole={props.organizationRole} isLiveEnvironmentOpen={props.isLiveEnvironmentOpen}><Suspense fallback={<div className="workspace-content" role="status">불러오는 중이에요.</div>}><ShellContent {...props} /></Suspense></TenantAccessProvider>;
}
