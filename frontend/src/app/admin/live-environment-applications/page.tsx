import type { Metadata } from "next";
import Link from "next/link";
import { LiveEnvironmentReviewAction } from "@/components/auth/LiveEnvironmentReviewAction";
import { listLiveEnvironmentApplications, type LiveEnvironmentApplicationResponse, type LiveEnvironmentApplicationStatus } from "@/lib/api/operator";
import { toDisplayMessage } from "@/lib/api/transport";
import { formatKstDateTime } from "@/lib/format";

export const metadata: Metadata = { title: "실제 환경 이용 신청" };

const STATUS_LABELS: Record<LiveEnvironmentApplicationStatus, string> = { pending: "검토 대기", approved: "승인", rejected: "거절" };
const STATUS_CLASSES: Record<LiveEnvironmentApplicationStatus, string> = { pending: "status-pending", approved: "status-active", rejected: "status-rejected" };
const BUSINESS_STATUS_LABELS: Record<LiveEnvironmentApplicationResponse["business_status"], string> = { operating: "계속사업자", suspended: "휴업자", unchecked: "자동 확인 못 함" };
const BUSINESS_STATUS_NOTES: Partial<Record<LiveEnvironmentApplicationResponse["business_status"], string>> = {
  suspended: "휴업 중인 사업자예요. 승인하기 전에 확인하세요.",
  unchecked: "신청할 때 국세청 조회를 하지 못했어요. 승인하기 전에 사업자등록번호를 직접 확인하세요.",
};

// 승인 뒤 첫 실제 결제의 응답에 든 상점아이디를 신청서의 값과 한 번 견준다.
function merchantCheck(application: LiveEnvironmentApplicationResponse): { text: string; isMismatch: boolean } | null {
  if (application.status !== "approved") return null;
  if (!application.merchant_id_checked_at) return { text: "실제 결제가 아직 없어요. 첫 실제 결제 때 확인해요.", isMismatch: false };
  if (application.observed_toss_payments_merchant_id === application.toss_payments_merchant_id) return { text: "첫 실제 결제의 상점아이디와 같아요.", isMismatch: false };
  return { text: `첫 실제 결제의 상점아이디(${application.observed_toss_payments_merchant_id})가 신청서와 달라요.`, isMismatch: true };
}

export default async function AdminLiveEnvironmentApplicationsPage({ searchParams }: { searchParams: Promise<{ status?: string }> }) {
  const query = await searchParams;
  const status = query.status && ["all", ...Object.keys(STATUS_LABELS)].includes(query.status) ? query.status : "pending";
  const result = await listLiveEnvironmentApplications(status === "all" ? undefined : status as LiveEnvironmentApplicationStatus);
  return <><header className="page-heading"><p className="eyebrow">운영자 업무</p><h1>실제 환경 이용 신청</h1><p className="muted">신청서와 국세청 조회 결과를 확인하고 승인하거나 사유를 적어 거절하세요.</p></header>
    <nav className="status-tabs" aria-label="신청 상태">{(Object.keys(STATUS_LABELS) as LiveEnvironmentApplicationStatus[]).map(value => <Link key={value} href={`?status=${value}`} aria-current={status === value ? "page" : undefined}>{STATUS_LABELS[value]}</Link>)}<Link href="?status=all" aria-current={status === "all" ? "page" : undefined}>전체</Link></nav>
    {!result.ok ? <div role="alert" className="failure-note"><p>{toDisplayMessage(result.error)}</p><Link href={`/admin/live-environment-applications?status=${status}`}>목록 다시 조회</Link></div> : <>
      {result.data.live_environment_applications.length === 0 && <div className="empty-panel"><h2>{status === "pending" ? "검토할 신청이 없어요" : "이 상태의 신청이 없어요"}</h2>{status === "pending" && <p>새 신청이 들어오면 여기에 보여요.</p>}<Link href={`/admin/live-environment-applications?status=${status}`}>목록 다시 조회</Link></div>}
      <div className="organization-list">{result.data.live_environment_applications.map(application => {
        const merchant = merchantCheck(application);
        const note = application.status === "pending" ? BUSINESS_STATUS_NOTES[application.business_status] : undefined;
        return <article className="organization-card" key={application.id}>
          <div className="organization-card-heading"><h2>{application.organization_name}</h2><span className={`status-label ${STATUS_CLASSES[application.status]}`}>{STATUS_LABELS[application.status]}</span></div>
          <p className="field-hint">신청 시각 {formatKstDateTime(application.created_at)} KST{application.reviewed_at ? ` / 처리 시각 ${formatKstDateTime(application.reviewed_at)} KST` : ""}</p>
          <dl className="detail-strip">
            <div><dt>사업자등록번호</dt><dd>{application.business_registration_number}</dd></div>
            <div><dt>국세청 사업자 상태</dt><dd>{BUSINESS_STATUS_LABELS[application.business_status]}</dd></div>
            <div><dt>과세유형</dt><dd>{application.tax_type ?? "확인 못 함"}</dd></div>
            <div><dt>토스페이먼츠 상점아이디</dt><dd>{application.toss_payments_merchant_id}</dd></div>
            {merchant && <div><dt>상점아이디 확인</dt><dd>{merchant.isMismatch ? <span className="status-label status-rejected">불일치</span> : null} {merchant.text}</dd></div>}
            {application.rejection_reason && <div><dt>거절 사유</dt><dd>{application.rejection_reason}</dd></div>}
          </dl>
          {note && <p className="field-hint">{note}</p>}
          {application.status === "pending" && <div className="review-actions"><LiveEnvironmentReviewAction id={application.id} target={application.organization_name} value="approved" /><LiveEnvironmentReviewAction id={application.id} target={application.organization_name} value="rejected" /></div>}
        </article>;
      })}</div>
    </>}
  </>;
}
