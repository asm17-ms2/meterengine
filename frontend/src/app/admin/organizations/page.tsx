import type { Metadata } from "next";
import Link from "next/link";
import { ReviewAction } from "@/components/auth/ReviewAction";
import { listOrganizations, listUserAccounts } from "@/lib/api/operator";
import { toDisplayMessage } from "@/lib/api/transport";
import { isLiveEnvironmentOpen, ORGANIZATION_STATUS_LABELS, type OrganizationStatus } from "@/lib/auth/types";
import { formatKstDateTime } from "@/lib/format";

export const metadata: Metadata = { title: "조직 목록" };

// 가입 대기와 가입 거절은 가입을 승인하던 때의 상태라 탭으로 두지 않고 전체 목록에서만 보인다.
const STATUS_TABS: OrganizationStatus[] = ["active", "suspended"];

export default async function AdminOrganizationsPage({ searchParams }: { searchParams: Promise<{ status?: string }> }) {
  const query = await searchParams;
  const status = query.status && Object.keys(ORGANIZATION_STATUS_LABELS).includes(query.status) ? query.status : "all";
  const [organizations, users] = await Promise.all([listOrganizations(status === "all" ? undefined : status as OrganizationStatus), listUserAccounts()]);
  return <><header className="page-heading"><p className="eyebrow">운영자 업무</p><h1>조직 목록</h1><p className="muted">가입한 조직과 소속 사용자를 보고, 실제 환경을 쓰는지 확인하세요.</p></header>
    <nav className="status-tabs" aria-label="조직 상태"><Link href="?status=all" aria-current={status === "all" ? "page" : undefined}>전체 조직</Link>{STATUS_TABS.map(value => <Link key={value} href={`?status=${value}`} aria-current={status === value ? "page" : undefined}>{ORGANIZATION_STATUS_LABELS[value]}</Link>)}</nav>
    {!organizations.ok ? <div role="alert" className="failure-note"><p>{toDisplayMessage(organizations.error)}</p><Link href={`/admin/organizations?status=${status}`}>목록 다시 조회</Link></div> : <>
      {!users.ok && <div role="alert" className="failure-note"><p>소속 사용자를 불러오지 못했어요.</p><Link href={`/admin/organizations?status=${status}`}>소속 사용자 다시 조회</Link></div>}
      {organizations.data.organizations.length === 0 && <div className="empty-panel"><h2>{status === "all" ? "가입한 조직이 없어요" : "이 상태의 조직이 없어요"}</h2><Link href={`/admin/organizations?status=${status}`}>목록 다시 조회</Link></div>}
      <div className="organization-list">{organizations.data.organizations.map(org => <article className="organization-card" key={org.id}><div className="organization-card-heading"><h2>{org.name}</h2><span className={`status-label status-${org.status}`}>{ORGANIZATION_STATUS_LABELS[org.status]}</span></div><p className="field-hint">가입일 {formatKstDateTime(org.created_at)} KST / 실제 환경 {isLiveEnvironmentOpen(org) ? "사용 중" : "승인 전"}</p><dl className="applicant-list"><dt>소속 사용자</dt><dd>{users.ok ? users.data.user_accounts.filter(user => user.organizations.some(member => member.id === org.id)).map(user => <div key={user.id}>{user.name} <span className="muted">{user.email}</span></div>) : "조회 실패"}</dd></dl>{org.status === "pending" && users.ok && <><p className="field-hint">가입에 승인이 필요하던 때에 신청한 조직이에요. 승인하면 다른 조직처럼 테스트 환경부터 쓸 수 있어요.</p><div className="review-actions"><ReviewAction kind="signup" id={org.id} target={org.name} value="active" label="가입 승인" /><ReviewAction kind="signup" id={org.id} target={org.name} value="rejected" label="가입 거절" /></div></>}</article>)}</div>
    </>}
  </>;
}
