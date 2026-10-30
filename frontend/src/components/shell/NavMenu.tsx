"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { useId } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname } from "next/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { useTenantScope } from "@/components/auth/TenantAccess";

const NAV_GROUPS = [
  { label: "워크스페이스", items: [{ href: "/overview", label: "개요" }, { href: "/customers", label: "고객" }] },
  { label: "사용량에서 수납까지", items: [{ href: "/usage", label: "사용량" }, { href: "/events", label: "수집 로그" }, { href: "/event-issues", label: "이벤트 매핑 진단" }, { href: "/event-rejections", label: "수집 거절" }, { href: "/billing", label: "청구 검토" }, { href: "/invoices", label: "확정 인보이스" }, { href: "/payments", label: "수납과 취소" }] },
  { label: "과금과 연동", items: [{ href: "/billable-metrics", label: "미터" }, { href: "/pricing", label: "요금 설계" }, { href: "/integrations", label: "API 키와 통보" }, { href: "/settings", label: "도입사 설정" }] },
  { label: "관리와 기록", items: [{ href: "/members", label: "사용자 관리" }, { href: "/audit", label: "감사 기록" }] },
];

export function NavMenu({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const id = useId();
  const canRead = useTenantScope("read");
  const search = useWorkspaceSearch().toString();
  const selected = NAV_GROUPS.flatMap(group => group.items).find(item => pathname === item.href || pathname.startsWith(item.href + "/"))?.href;
  if (!canRead) return <nav aria-label="도입사 업무"><Link href="/organizations" onClick={onNavigate}>도입사 선택</Link></nav>;
  // 페이지 이동 링크라 menu 역할 없이 그룹 제목과 링크 목록으로 둔다.
  return <nav aria-label="도입사 업무">{NAV_GROUPS.map((group, index) => <div key={group.label} className="nav-group"><div className="nav-group-title" id={`${id}-group-${index}`}>{group.label}</div><ul className="nav-list" aria-labelledby={`${id}-group-${index}`}>{group.items.map(item => <li key={item.href}><Link className="nav-link" href={workspaceHref(item.href, search)} aria-current={selected === item.href ? "page" : undefined} onClick={onNavigate}>{item.label}</Link></li>)}</ul></div>)}</nav>;
}
