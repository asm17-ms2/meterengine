import { ConsoleShell } from "@/components/shell/ConsoleShell";
import { requireTenantSession } from "@/lib/auth/session";
import { headers } from "next/headers";
import Link from "next/link";

export default async function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const session = await requireTenantSession();
  const incoming = await headers();
  const expectedOrganization = incoming.get("x-meterengine-organization-id");
  if (expectedOrganization && expectedOrganization !== session.organization!.id) return <main className="access-page" id="main-content"><h1>도입사 선택이 바뀌었습니다</h1><p>다른 탭에서 선택한 도입사가 바뀌어 이 화면의 요청을 중단했습니다. 도입사를 다시 선택하세요.</p><Link href="/organizations">도입사 선택</Link></main>;
  return <ConsoleShell organizationId={session.organization!.id} organizationLabel={session.organization!.name} userLabel={session.user_account.name} isOperator={session.user_account.role === "operator"} organizationRole={session.organization_role} scopes={session.scopes}>{children}</ConsoleShell>;
}
