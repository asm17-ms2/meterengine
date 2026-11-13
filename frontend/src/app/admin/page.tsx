import type { Metadata } from "next";
import Link from "next/link";
import { operatorRequest } from "@/lib/api/operator";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceTable, WorkspaceError } from "@/components/workspace/WorkspaceView";

type OperationsResponse = { calculated_at: string; pending_live_environment_application_count: number; active_organization_count: number; user_account_count: number; pending_payment_count: number; failed_payment_count: number; failed_webhook_count: number; jobs: Record<string, unknown>[] };

export const metadata: Metadata = { title: "운영 현황" };

export default async function AdminPage() {
  const result = await operatorRequest<OperationsResponse>("/v1/operations");
  if (!result.ok) return <WorkspaceError message={result.error.message} />;
  const data = result.data;
  return <><WorkspaceHeader title="운영 현황" description="실제 환경 이용 신청과 처리 지연, 통보 실패를 확인하세요." /><section className="finance-flow"><div><span>검토 대기 신청</span><strong>{data.pending_live_environment_application_count}건</strong><Link href="/admin/live-environment-applications">실제 환경 이용 신청 검토</Link></div><div><span>사용 중 조직</span><strong>{data.active_organization_count}개</strong><Link href="/admin/organizations?status=active">조직 목록</Link></div><div><span>결과 확인 중 결제</span><strong>{data.pending_payment_count}건</strong><small>상태 확정 작업을 확인하세요</small></div><div><span>발송 실패 통보</span><strong>{data.failed_webhook_count}건</strong><small>보관하지 않은 수신 주소의 발송 실패</small></div></section><section className="workspace-panel"><h2>예약 작업</h2><p className="field-hint">실행 기록을 기준으로 보여 줘요. 아직 한 번도 실행되지 않은 작업은 정상으로 보지 않아요.</p><WorkspaceTable rows={data.jobs} rowKey="job_name" columns={[{ key: "job_name", label: "작업" }, { key: "latest_status", label: "최근 상태", isStatus: true }, { key: "last_started_at", label: "마지막 시작" }, { key: "last_success_at", label: "마지막 성공" }]} /></section><section className="workspace-panel"><h2>확인할 항목</h2><p>최근 24시간 결제 실패 {data.failed_payment_count}건 / 사용자 계정 {data.user_account_count}개</p><Link href="/admin/users">사용자와 권한</Link></section></>;
}
