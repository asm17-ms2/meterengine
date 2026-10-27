import Link from "next/link";
import { operatorRequest } from "@/lib/api/operator";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceTable, WorkspaceError } from "@/components/workspace/WorkspaceView";

type OperationsResponse = { calculated_at: string; pending_organization_count: number; active_organization_count: number; user_account_count: number; pending_payment_count: number; failed_payment_count: number; failed_webhook_count: number; jobs: Record<string, unknown>[] };

export default async function AdminPage() {
  const result = await operatorRequest<OperationsResponse>("/v1/operations");
  if (!result.ok) return <WorkspaceError message={result.error.message} />;
  const data = result.data;
  return <><WorkspaceHeader title="서비스 운영 현황" kicker="MeterEngine 운영" description="가입 승인과 처리 지연, 통보 실패를 확인하세요. 도입사 업무 화면과 구분된 운영자 전용 조회입니다." /><section className="finance-flow"><div><span>승인 대기</span><strong>{data.pending_organization_count}개</strong><Link href="/admin/organizations">가입 신청 검토</Link></div><div><span>사용 중 도입사</span><strong>{data.active_organization_count}개</strong><Link href="/admin/organizations?status=active">도입사 조회</Link></div><div><span>결과 확인 중 결제</span><strong>{data.pending_payment_count}건</strong><small>상태 확정 작업을 확인하세요</small></div><div><span>발송 실패 통보</span><strong>{data.failed_webhook_count}건</strong><small>재시도 상한에 도달한 전송</small></div></section><section className="workspace-panel"><h2>예약 작업</h2><p className="field-hint">DB 실행 기록을 기준으로 표시합니다. 첫 실행 전의 빈 시각을 정상 실행으로 간주하지 않습니다.</p><WorkspaceTable rows={data.jobs} rowKey="job_name" columns={[{ key: "job_name", label: "작업" }, { key: "latest_status", label: "최근 상태", isStatus: true }, { key: "last_started_at", label: "최근 시작" }, { key: "last_success_at", label: "마지막 성공" }]} /></section><section className="workspace-panel"><h2>확인할 항목</h2><p>최근 24시간 결제 실패 {data.failed_payment_count}건 / 사용자 계정 {data.user_account_count}개</p><Link href="/admin/users">사용자와 권한 관리</Link></section></>;
}
