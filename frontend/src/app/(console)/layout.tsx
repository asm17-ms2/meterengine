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
