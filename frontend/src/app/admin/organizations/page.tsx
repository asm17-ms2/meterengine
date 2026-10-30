import type { Metadata } from "next";
import Link from "next/link";
import { ReviewAction } from "@/components/auth/ReviewAction";
import { listOrganizations, listUserAccounts } from "@/lib/api/operator";
import { toDisplayMessage } from "@/lib/api/transport";
import { ORGANIZATION_STATUS_LABELS, type OrganizationStatus } from "@/lib/auth/types";
import { formatKstDateTime } from "@/lib/format";

export const metadata: Metadata = { title: "가입 승인과 도입사" };

export default async function AdminOrganizationsPage({ searchParams }: { searchParams: Promise<{ status?: string }> }) {
  const query = await searchParams;
  const status = query.status && ["all", ...Object.keys(ORGANIZATION_STATUS_LABELS)].includes(query.status) ? query.status : "pending";
  const [organizations, users] = await Promise.all([listOrganizations(status === "all" ? undefined : status as OrganizationStatus), listUserAccounts()]);
  return <><header className="page-heading"><p className="eyebrow">운영자 업무</p><h1>가입 승인과 도입사</h1><p className="muted">신청한 도입사와 담당자를 확인하고 가입을 승인하거나 거절하세요.</p></header>
    <nav className="status-tabs" aria-label="도입사 상태"><Link href="?status=pending" aria-current={status === "pending" ? "page" : undefined}>승인 대기</Link><Link href="?status=all" aria-current={status === "all" ? "page" : undefined}>전체 도입사</Link>{(["active", "rejected", "suspended"] as OrganizationStatus[]).map(value => <Link key={value} href={`?status=${value}`} aria-current={status === value ? "page" : undefined}>{ORGANIZATION_STATUS_LABELS[value]}</Link>)}</nav>
    {!organizations.ok ? <div role="alert" className="failure-note"><p>{toDisplayMessage(organizations.error)}</p><Link href={`/admin/organizations?status=${status}`}>목록 다시 조회</Link></div> : <>
      {!users.ok && <div role="alert" className="failure-note"><p>담당자 정보를 불러오지 못했습니다. 승인 전에 담당자를 다시 확인하세요.</p><Link href={`/admin/organizations?status=${status}`}>담당자 다시 조회</Link></div>}
      {organizations.data.organizations.filter(org => status === "all" || org.status === status).length === 0 && <div className="empty-panel"><h2>{status === "pending" ? "승인 대기 중인 신청이 없습니다" : "이 상태의 도입사가 없습니다"}</h2><p>새 신청은 가입 신청을 마친 뒤 이 목록에서 확인할 수 있습니다.</p><Link href={`/admin/organizations?status=${status}`}>목록 새로고침</Link></div>}
      <div className="organization-list">{organizations.data.organizations.filter(org => status === "all" || org.status === status).map(org => <article className="organization-card" key={org.id}><div className="organization-card-heading"><h2>{org.name}</h2><span className={`status-label status-${org.status}`}>{ORGANIZATION_STATUS_LABELS[org.status]}</span></div><p className="field-hint">신청일 {formatKstDateTime(org.created_at)} KST</p><dl className="applicant-list"><dt>소속 사용자</dt><dd>{users.ok ? users.data.user_accounts.filter(user => user.organizations.some(member => member.id === org.id)).map(user => <div key={user.id}>{user.name} <span className="muted">{user.email}</span></div>) : "조회 실패"}</dd></dl>{org.status === "pending" && users.ok && <div className="review-actions"><ReviewAction kind="signup" id={org.id} target={org.name} value="active" label="가입 승인" /><ReviewAction kind="signup" id={org.id} target={org.name} value="rejected" label="가입 거절" /></div>}</article>)}</div>
    </>}
  </>;
}
