import type { Metadata } from "next";
import Link from "next/link";
import { ReviewAction } from "@/components/auth/ReviewAction";
import { CreateOperatorButton } from "@/components/auth/CreateOperatorButton";
import { listUserAccounts } from "@/lib/api/operator";
import { toDisplayMessage } from "@/lib/api/transport";
import type { UserAccountResponse } from "@/lib/auth/types";

export const metadata: Metadata = { title: "사용자와 권한" };

// 운영자 역할과 조직 소속은 함께 가질 수 없어, 바꿀 수 없는 계정은 이유를 버튼 아래에 보인다.
function roleChangeBlock(user: UserAccountResponse, operatorCount: number): string | undefined {
  if (user.role === "operator") return operatorCount <= 1 ? "마지막 플랫폼 운영자의 권한은 제거할 수 없어요." : undefined;
  return user.organizations.length ? "조직에 소속된 계정은 플랫폼 운영자로 바꿀 수 없어요." : undefined;
}

export default async function AdminUsersPage() {
  const users = await listUserAccounts();
  const operatorCount = users.ok ? users.data.user_accounts.filter(user => user.role === "operator").length : 0;
  return <><header className="page-heading"><h1>사용자와 권한</h1><p className="muted">플랫폼 운영자 계정을 새로 만들거나, 소속 조직이 없는 계정의 운영 권한을 바꾸세요. 조직의 사용자는 그 조직의 사용자 관리에서 초대하거나 추가해요.</p><div className="workspace-actions"><CreateOperatorButton /></div></header>
    {!users.ok ? <div role="alert"><p>{toDisplayMessage(users.error)}</p><Link href="/admin/users">사용자 다시 조회</Link></div> : <div className="organization-list">{users.data.user_accounts.map(user => <article className="organization-card" key={user.id}><div className="organization-card-heading"><h2>{user.name}</h2><span className="status-label">{user.role === "operator" ? "플랫폼 운영자" : "일반 계정"}</span></div><p>{user.email}</p><p className="muted">{user.organizations.length ? user.organizations.map(org => org.name).join(", ") : "소속 조직 없음"}</p><div className="workspace-actions"><ReviewAction key={user.role} kind="role" id={user.id} target={`${user.name} (${user.email})`} value={user.role === "operator" ? "member" : "operator"} disabledReason={roleChangeBlock(user, operatorCount)} label={user.role === "operator" ? "플랫폼 운영 권한 제거" : "플랫폼 운영자로 변경"} /></div></article>)}</div>}
  </>;
}
