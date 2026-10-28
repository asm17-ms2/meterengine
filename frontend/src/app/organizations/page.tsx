import type { Metadata } from "next";
import Link from "next/link";
import { LogoutButton, OrganizationPicker } from "@/components/auth/SessionControls";
import { requireSession } from "@/lib/auth/session";
import { ORGANIZATION_STATUS_LABELS } from "@/lib/auth/types";

export const metadata: Metadata = { title: "도입사 선택" };

export default async function OrganizationsPage() {
  const session = await requireSession();
  return <main id="main-content" className="access-page"><Link className="wordmark" href="/">MeterEngine</Link><header><p className="eyebrow">{session.user_account.name}</p><h1>도입사 선택</h1><p>소속된 도입사로 이동하세요. 사용 중인 도입사에서만 업무를 진행할 수 있습니다.</p></header>
    {session.organization && session.organization.status !== "active" && <p role="status">현재 도입사: {session.organization.name}, {ORGANIZATION_STATUS_LABELS[session.organization.status]}. 이용 가능 여부를 운영자에게 확인하세요.</p>}
    {session.organizations.length ? <OrganizationPicker session={session} /> : <p>{session.user_account.role === "operator" ? "소속된 도입사가 없습니다. 운영자 업무는 도입사 없이 이용할 수 있습니다." : "소속된 도입사가 없습니다. 운영자에게 소속 추가를 요청하세요."}</p>}
    <div className="access-actions">{session.user_account.role === "operator" && <Link href="/admin/organizations">운영자 화면으로</Link>}<LogoutButton /></div>
  </main>;
}
