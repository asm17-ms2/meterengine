import type { Metadata } from "next";
import Link from "next/link";
import { ReviewAction } from "@/components/auth/ReviewAction";
import { AttachOrganizationButton } from "@/components/auth/AttachOrganizationButton";
import { CreateOperatorButton } from "@/components/auth/CreateOperatorButton";
import { listOrganizations, listUserAccounts } from "@/lib/api/operator";
import { toDisplayMessage } from "@/lib/api/transport";

export const metadata: Metadata = { title: "사용자와 플랫폼 권한" };

export default async function AdminUsersPage() {
  const [users, organizations] = await Promise.all([listUserAccounts(), listOrganizations()]);
  const operatorCount = users.ok ? users.data.user_accounts.filter(user => user.role === "operator").length : 0;
  return <><header className="page-heading"><h1>사용자와 플랫폼 권한</h1><p className="muted">새 플랫폼 운영자 계정을 만들거나, 기존 계정의 운영 권한을 변경하세요. 도입사 내부 관리자는 해당 도입사의 사용자 관리에서 추가하세요.</p><div className="workspace-actions"><CreateOperatorButton /></div></header>
    {!organizations.ok && <p role="alert">도입사 목록을 확인하지 못했습니다. {toDisplayMessage(organizations.error)}</p>}
    {!users.ok ? <div role="alert"><p>{toDisplayMessage(users.error)}</p><Link href="/admin/users">사용자 다시 조회</Link></div> : <div className="organization-list">{users.data.user_accounts.map(user => <article className="organization-card" key={user.id}><div className="organization-card-heading"><h2>{user.name}</h2><span className="status-label">{user.role === "operator" ? "플랫폼 운영자" : "일반 계정"}</span></div><p>{user.email}</p><p className="muted">{user.organizations.length ? user.organizations.map(org => org.name).join(", ") : "소속 도입사 없음"}</p><div className="workspace-actions">{organizations.ok && <AttachOrganizationButton key={user.organizations.map(organization => organization.id).join(",")} userId={user.id} userLabel={`${user.name} (${user.email})`} organizations={organizations.data.organizations.filter(organization => organization.status === "active" && !user.organizations.some(membership => membership.id === organization.id))} />}<ReviewAction key={user.role} kind="role" id={user.id} target={`${user.name} (${user.email})`} value={user.role === "operator" ? "member" : "operator"} disabledReason={user.role === "operator" && operatorCount <= 1 ? "마지막 플랫폼 운영자의 권한은 제거할 수 없습니다." : undefined} label={user.role === "operator" ? "플랫폼 운영 권한 제거" : "플랫폼 운영자로 변경"} /></div></article>)}</div>}
  </>;
}
