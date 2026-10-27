"use client";

import { Button, Layout, Menu } from "antd";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";

const MENU_ITEMS = [
  { href: "/", label: "개요" },
  { href: "/customers?view=operator", label: "고객 화면 시연" },
  { href: "/events", label: "이벤트 보내기" },
  { href: "/events/csv", label: "CSV 보내기" },
  { href: "/webhooks", label: "웹훅 수신" },
  { href: "/bridge", label: "브리지 설정" },
];

export function AppMenu() {
  const pathname = usePathname();
  const search = useSearchParams();
  if (pathname === "/customers" && search.get("view") !== "operator") return <nav className="demo-preview-navigation" aria-label="시연 화면 구분"><span>고객 화면 미리보기</span><Link href={`/customers?${new URLSearchParams({ ...Object.fromEntries(search.entries()), view: "operator" })}`}>시연 운영으로 돌아가기</Link></nav>;
  return (
    <Layout.Sider theme="light" width={200} breakpoint="lg" collapsedWidth={0} zeroWidthTriggerStyle={{ top: 16 }} trigger={<Button type="text" aria-label="탐색 메뉴 열고 닫기" style={{ width: 40, height: 40, padding: 0 }}>메뉴</Button>}>
      <div style={{ padding: "16px 24px", fontWeight: 600 }}>MeterEngine 테스트 웹</div>
      <Menu
        mode="inline"
        selectedKeys={[pathname === "/customers" ? "/customers?view=operator" : pathname]}
        items={MENU_ITEMS.map((item) => ({ key: item.href, label: <Link href={item.href}>{item.label}</Link> }))}
      />
    </Layout.Sider>
  );
}
