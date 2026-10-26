"use client";

import { Menu } from "antd";
import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/events", label: "이벤트 로그" },
  { href: "/usage", label: "사용량 집계" },
  { href: "/billing", label: "청구 예정액" },
  { href: "/invoices", label: "인보이스" },
  { href: "/customers", label: "고객" },
  { href: "/billable-metrics", label: "미터" },
  { href: "/billable-metric-prices", label: "가격" },
] as const;

export function NavMenu({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const selectedKeys = NAV_ITEMS.filter((item) => pathname.startsWith(item.href)).map(
    (item) => item.href,
  );

  return (
    <nav>
      <Menu
        mode="inline"
        tabIndex={-1}
        selectedKeys={selectedKeys}
        onClick={onNavigate}
        style={{ borderInlineEnd: 0 }}
        items={NAV_ITEMS.map((item) => ({
          key: item.href,
          label: (
            <Link
              href={item.href}
              aria-current={selectedKeys.includes(item.href) ? "page" : undefined}
            >
              {item.label}
            </Link>
          ),
        }))}
      />
    </nav>
  );
}
