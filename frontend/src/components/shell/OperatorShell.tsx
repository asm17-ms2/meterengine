"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogoutButton } from "@/components/auth/SessionControls";

export function OperatorShell({ name, children }: { name: string; children: React.ReactNode }) {
  const path = usePathname();
  return <div className="operator-layout"><header className="operator-header"><Link className="wordmark" href="/admin">MeterEngine</Link><span className="operator-badge">MeterEngine 운영자</span><span className="operator-user">{name}</span><LogoutButton /></header><nav className="operator-nav" aria-label="MeterEngine 운영 메뉴"><Link href="/admin" aria-current={path === "/admin" ? "page" : undefined}>운영 현황</Link><Link href="/admin/organizations" aria-current={path === "/admin/organizations" ? "page" : undefined}>가입 승인과 도입사</Link><Link href="/admin/users" aria-current={path === "/admin/users" ? "page" : undefined}>사용자와 권한</Link><Link href="/organizations">내 도입사로 이동</Link></nav><main id="main-content" className="operator-content">{children}</main></div>;
}
