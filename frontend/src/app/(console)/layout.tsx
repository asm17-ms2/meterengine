import { ConsoleShell } from "@/components/shell/ConsoleShell";
import { config } from "@/lib/config";
import { requireTenantSession } from "@/lib/auth/session";
import { headers } from "next/headers";
import Link from "next/link";

export default async function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const session = await requireTenantSession();
  const incoming = await headers();
  const expectedOrganization = incoming.get("x-meterengine-organization-id");
  if (expectedOrganization && expectedOrganization !== session.organization!.id) return <main className="access-page" id="main-content"><h1>선택한 조직이 바뀌었어요</h1><p>다른 탭에서 조직을 바꿨어요. 다른 조직에 잘못 반영되지 않도록 이 화면은 열지 않았어요. 볼 조직을 다시 선택하세요.</p><Link href="/organizations">조직 선택</Link></main>;
  return <ConsoleShell organizationId={session.organization!.id} organizationLabel={session.organization!.name} canSwitchOrganization={session.organizations.length > 1} userLabel={session.user_account.name} isOperator={session.user_account.role === "operator"} organizationRole={session.organization_role} scopes={session.scopes} showLegacyEnvironment={config.showLegacyEnvironment}>{children}</ConsoleShell>;
}
