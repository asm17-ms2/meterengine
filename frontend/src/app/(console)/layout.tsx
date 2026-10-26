import { ConsoleShell } from "@/components/shell/ConsoleShell";
import { config } from "@/lib/config";

export default function ConsoleLayout({ children }: { children: React.ReactNode }) {
  return (
    <ConsoleShell
      organizationLabel={`관리자 콘솔, ${config.organizationName} (${config.organizationId})`}
    >
      {children}
    </ConsoleShell>
  );
}
