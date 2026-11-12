import type { Metadata } from "next";
import Link from "next/link";
import { LogoutButton, OrganizationPicker } from "@/components/auth/SessionControls";
import { requireSession } from "@/lib/auth/session";
import { ORGANIZATION_STATUS_LABELS } from "@/lib/auth/types";

export const metadata: Metadata = { title: "조직 선택" };

export default async function OrganizationsPage() {
  const session = await requireSession();
  return <main id="main-content" className="access-page"><Link className="wordmark" href="/">MeterEngine</Link><header><p className="eyebrow">{session.user_account.name}</p><h1>조직 선택</h1><p>이동할 조직을 고르세요. 사용 중인 조직에서만 콘솔을 쓸 수 있어요.</p></header>
    {session.organization && session.organization.status !== "active" && <p role="status">현재 조직: {session.organization.name}, {ORGANIZATION_STATUS_LABELS[session.organization.status]}. 지금은 이 조직의 콘솔을 이용할 수 없어요.</p>}
    {session.organizations.length ? <OrganizationPicker session={session} /> : <p>{session.user_account.role === "operator" ? "소속된 조직이 없어요. 운영자 업무는 조직 없이 이용할 수 있어요." : "소속된 조직이 없어요. 가입할 조직의 대표관리자에게 초대 링크를 요청하세요."}</p>}
    <div className="access-actions">{session.user_account.role === "operator" && <Link href="/admin">운영자 화면으로</Link>}<LogoutButton /></div>
  </main>;
}
