"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { useId, useState } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname } from "next/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { useTenantScope } from "@/components/auth/TenantAccess";
import { DOCS_URL } from "@/lib/docs";

type NavItem = { href: string; label: string };
// 하위 화면을 가진 항목은 링크가 아니라 누르면 하위 화면이 펼쳐지는 버튼이다.
type NavParent = { label: string; children: NavItem[] };
type NavEntry = NavItem | NavParent;

// 맨 위 묶음(홈, 고객)은 제목 없이 둔다.
const NAV_GROUPS: { label?: string; items: NavEntry[] }[] = [
  { items: [{ href: "/overview", label: "홈" }, { href: "/customers", label: "고객" }] },
  { label: "사용량과 청구", items: [
    { href: "/usage", label: "사용량" },
    { label: "이벤트", children: [{ href: "/events", label: "수집 이벤트" }, { href: "/event-issues", label: "과금에서 빠진 이벤트" }, { href: "/event-rejections", label: "수집 거절" }] },
    { href: "/billing", label: "청구 검토" }, { href: "/invoices", label: "확정 청구서" }, { href: "/payments", label: "수납과 취소" },
  ] },
  { label: "요금과 연동", items: [{ href: "/billable-metrics", label: "미터" }, { href: "/pricing", label: "요금 설정" }, { href: "/integrations", label: "API 키와 웹훅" }] },
  { label: "조직", items: [{ href: "/settings", label: "조직 설정" }, { href: "/members", label: "사용자 관리" }, { href: "/audit", label: "감사 기록" }] },
];

function isParent(entry: NavEntry): entry is NavParent {
  return "children" in entry;
}

const NAV_ITEMS = NAV_GROUPS.flatMap(group => group.items).flatMap(entry => isParent(entry) ? entry.children : [entry]);

export function NavMenu({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const id = useId();
  const canRead = useTenantScope("read");
  const search = useWorkspaceSearch().toString();
  const selected = NAV_ITEMS.find(item => pathname === item.href || pathname.startsWith(item.href + "/"))?.href;
  // 누른 적이 없으면 지금 보는 화면이 든 묶음만 펼쳐 둔다.
  const [openByLabel, setOpenByLabel] = useState<Record<string, boolean>>({});
  const docsLink = <a className="nav-link nav-docs-link" href={DOCS_URL} target="_blank" rel="noopener noreferrer">가이드와 API 문서</a>;
  if (!canRead) return <nav aria-label="주 메뉴"><Link href="/organizations" onClick={onNavigate}>조직 선택</Link>{docsLink}</nav>;
  const isOpen = (parent: NavParent) => openByLabel[parent.label] ?? parent.children.some(child => child.href === selected);
  const link = (item: NavItem) => <li key={item.href}><Link className="nav-link" href={workspaceHref(item.href, search)} aria-current={selected === item.href ? "page" : undefined} onClick={onNavigate}>{item.label}</Link></li>;
  // 페이지 이동 링크라 menu 역할 없이 그룹 제목과 링크 목록으로 둔다.
  return <nav aria-label="주 메뉴">{NAV_GROUPS.map((group, index) => <div key={group.label ?? index} className="nav-group">{group.label && <div className="nav-group-title" id={`${id}-group-${index}`}>{group.label}</div>}<ul className="nav-list" aria-labelledby={group.label ? `${id}-group-${index}` : undefined}>{group.items.map(entry => isParent(entry)
    ? <li key={entry.label}><button type="button" className="nav-parent" id={`${id}-parent-${entry.label}`} aria-expanded={isOpen(entry)} aria-controls={`${id}-children-${entry.label}`} onClick={() => setOpenByLabel(current => ({ ...current, [entry.label]: !isOpen(entry) }))}>{entry.label}</button>{isOpen(entry) && <ul className="nav-list nav-sublist" id={`${id}-children-${entry.label}`} aria-labelledby={`${id}-parent-${entry.label}`}>{entry.children.map(link)}</ul>}</li>
    : link(entry))}</ul></div>)}<div className="nav-group nav-docs">{docsLink}</div></nav>;
}
