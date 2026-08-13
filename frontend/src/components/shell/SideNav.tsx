"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/events", label: "이벤트 로그" },
  { href: "/usage", label: "사용량 집계" },
  { href: "/billing", label: "청구 예정액" },
] as const;

export function SideNav() {
  const pathname = usePathname();

  return (
    <nav className="side-nav">
      {NAV_ITEMS.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className="side-nav__item"
          aria-current={pathname.startsWith(item.href) ? "page" : undefined}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
