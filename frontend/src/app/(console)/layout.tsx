import { Suspense } from "react";

import { DevStateSwitch } from "@/components/shell/DevStateSwitch";
import { SideNav } from "@/components/shell/SideNav";
import { TopNav } from "@/components/shell/TopNav";

export default function ConsoleLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col">
      <TopNav />
      <div className="console-layout">
        <aside className="console-sidebar">
          <SideNav />
          {/* DevStateSwitch가 useSearchParams()를 쓴다. Suspense로 감싸지 않으면
              이 레이아웃을 쓰는 페이지가 prerender에서 통째로 바일아웃된다. */}
          <Suspense fallback={null}>
            <DevStateSwitch />
          </Suspense>
        </aside>
        <main className="console-main">
          <div className="console-content">{children}</div>
        </main>
      </div>
    </div>
  );
}
