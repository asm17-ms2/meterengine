import { ConsoleShell } from "@/components/shell/ConsoleShell";
import { config } from "@/lib/config";
import { requireTenantSession } from "@/lib/auth/session";
import { isLiveEnvironmentOpen as readLiveEnvironmentOpen } from "@/lib/auth/types";
import { headers } from "next/headers";
import Link from "next/link";
import { redirect } from "next/navigation";
import { isLockedEnvironment, LIVE_ENVIRONMENT_PATH, readEnvironment } from "@/lib/environment";
import { workspaceHref } from "@/lib/workspace-href";

export default async function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const session = await requireTenantSession();
  const incoming = await headers();
  const expectedOrganization = incoming.get("x-meterengine-organization-id");
  if (expectedOrganization && expectedOrganization !== session.organization!.id) return <main className="access-page" id="main-content"><h1>선택한 조직이 바뀌었어요</h1><p>다른 탭에서 조직을 바꿨어요. 다른 조직에 잘못 반영되지 않도록 이 화면은 열지 않았어요. 볼 조직을 다시 선택하세요.</p><Link href="/organizations">조직 선택</Link></main>;
  const isLiveEnvironmentOpen = readLiveEnvironmentOpen(session.organization!);
  // 주소에 환경을 직접 적어 들어와도 잠긴 환경의 화면은 열지 않고 이용 신청 화면으로 보낸다.
  if (isLockedEnvironment(readEnvironment(incoming.get("x-meterengine-environment")), isLiveEnvironmentOpen)) redirect(workspaceHref(LIVE_ENVIRONMENT_PATH, new URLSearchParams({ organization_id: session.organization!.id })));
  return <ConsoleShell organizationId={session.organization!.id} organizationLabel={session.organization!.name} canSwitchOrganization={session.organizations.length > 1} userLabel={session.user_account.name} isOperator={session.user_account.role === "operator"} organizationRole={session.organization_role} scopes={session.scopes} showLegacyEnvironment={config.showLegacyEnvironment} isLiveEnvironmentOpen={isLiveEnvironmentOpen}>{children}</ConsoleShell>;
}
