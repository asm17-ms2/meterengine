import type { Metadata } from "next";
import { requireTenantSession } from "@/lib/auth/session";
import { hasScope } from "@/lib/auth/permissions";
import { isLiveEnvironmentOpen } from "@/lib/auth/types";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { LiveEnvironmentShellRefresh } from "@/components/workspace/LiveEnvironmentShellRefresh";

type LiveEnvironmentApplication = { id: string; business_registration_number: string; toss_payments_merchant_id: string; status: "pending" | "approved" | "rejected"; rejection_reason: string | null; created_at: string; reviewed_at: string | null };

const APPLICATION_STATUS_LABELS = { pending: "확인 중", approved: "승인", rejected: "거절" };

export const metadata: Metadata = { title: "실제 환경 이용 신청" };

export default async function LiveEnvironmentPage() {
  const session = await requireTenantSession();
  const isOpen = isLiveEnvironmentOpen(session.organization!);
  const result = await workspaceFetch<{ live_environment_applications: LiveEnvironmentApplication[] }>("/v1/organization/live-environment-applications");
  const applications = result.ok ? result.data.live_environment_applications : [];
  // 서버가 최근 신청부터 돌려준다.
  const latest = applications[0];
  const canApply = !isOpen && result.ok && latest?.status !== "pending";
  return <>
    {isOpen && <LiveEnvironmentShellRefresh />}
    <WorkspaceHeader title="실제 환경 이용 신청" description={isOpen ? "실제 환경에서는 실제 고객에게 청구하고 결제를 받아요." : "실제 고객에게 청구하고 결제를 받으려면 실제 환경 이용을 신청하세요."} />
    {!result.ok ? <WorkspaceError message={result.error.message} status={result.error.status} /> : <section className="workspace-panel" aria-live="polite">{isOpen
      ? <><h2>실제 환경을 쓰고 있어요</h2><p>화면 위 업무 환경에서 실제 환경을 고르면 실제 고객에게 청구하고 결제를 받을 수 있어요.</p></>
      : latest?.status === "pending" ? <><h2>신청을 받았어요</h2><p>승인되면 화면 위 업무 환경에서 실제 환경을 고를 수 있어요. 결과는 이 화면에서 확인하세요. 그동안 테스트 환경은 그대로 쓰면 돼요.</p></>
        : latest?.status === "rejected" ? <><h2>신청이 승인되지 않았어요</h2><p>사유: {latest.rejection_reason}</p><p>내용을 고쳐 다시 신청하세요.</p></>
          : <><h2>실제 환경은 이용 신청이 승인되면 쓸 수 있어요</h2><p>사업자등록번호와 토스페이먼츠 실결제 키를 준비해 신청하세요. 테스트 환경은 지금 그대로 쓰면 돼요.</p></>}</section>}
    {canApply && (hasScope(session.scopes, "admin")
      ? <ActionPanel isInline title="이용 신청서" operation="createLiveEnvironmentApplication" label="이용 신청" description="신청하면 사업자등록번호와 토스페이먼츠 키를 바로 확인하고, 문제가 있으면 이유를 알려 줘요." fields={[
        { name: "business_registration_number", label: "사업자등록번호", hint: "숫자 10자리예요. 하이픈(-)은 넣어도 돼요.", maxLength: 20 },
        { name: "toss_payments_merchant_id", label: "토스페이먼츠 상점아이디(MID)", hint: "토스페이먼츠와 계약하고 받은 상점아이디예요.", maxLength: 64 },
        { name: "toss_payments_client_key", label: "실결제 클라이언트 키", hint: "live_ck_로 시작해요." },
        { name: "toss_payments_secret_key", label: "실결제 시크릿 키", type: "password", hint: "live_sk_로 시작해요. 암호화해 저장해서 나중에 다시 볼 수 없어요." },
      ]} />
      : <section className="workspace-panel"><h2>이용 신청서</h2><p className="muted">전체 관리 권한이 있어야 신청할 수 있어요. 대표관리자에게 권한을 요청하세요.</p></section>)}
    {applications.length > 0 && <section className="workspace-panel"><h2>신청 내역</h2><WorkspaceTable label="신청 내역" rows={applications} columns={[{ key: "created_at", label: "신청 시각", isDate: true }, { key: "status", label: "상태", isStatus: true, statusLabels: APPLICATION_STATUS_LABELS }, { key: "business_registration_number", label: "사업자등록번호" }, { key: "toss_payments_merchant_id", label: "상점아이디" }, { key: "rejection_reason", label: "거절 사유" }, { key: "reviewed_at", label: "처리 시각", isDate: true }]} /></section>}
  </>;
}
