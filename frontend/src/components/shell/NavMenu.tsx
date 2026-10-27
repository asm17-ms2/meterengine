"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { Menu } from "antd";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname } from "next/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { useTenantScope } from "@/components/auth/TenantAccess";

const NAV_GROUPS = [
  { label: "워크스페이스", items: [ { href: "/customers", label: "고객" }] },
  { label: "사용량에서 수납까지", items: [{ href: "/usage", label: "사용량" }, { href: "/events", label: "수집 로그" },{ href: "/billing", label: "청구 검토" }, { href: "/invoices", label: "확정 인보이스" }] },
  { label: "과금과 연동", items: [ { href: "/integrations", label: "API 키와 통보" }] },
  { label: "관리와 기록", items: [{ href: "/members", label: "사용자 관리" }] }];

export function NavMenu({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const canRead = useTenantScope("read");
  const search = useWorkspaceSearch().toString();
  const selected = NAV_GROUPS.flatMap(group => group.items).find(item => pathname === item.href || pathname.startsWith(item.href + "/"))?.href;
  if (!canRead) return <nav aria-label="도입사 업무"><Link href="/organizations" onClick={onNavigate}>도입사 선택</Link></nav>;
  return <nav aria-label="도입사 업무"><Menu mode="inline" selectedKeys={selected ? [selected] : []} onClick={onNavigate} items={NAV_GROUPS.map(group => ({ type: "group", key: group.label, label: group.label, children: group.items.map(item => ({ key: item.href, label: <Link href={workspaceHref(item.href, search)} aria-current={selected === item.href ? "page" : undefined}>{item.label}</Link> })) }))} /></nav>;
}
