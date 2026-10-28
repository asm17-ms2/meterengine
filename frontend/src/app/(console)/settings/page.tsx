import { requireTenantSession } from "@/lib/auth/session";
import { readArchiveStatus } from "@/lib/list-query";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";


export default async function SettingsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  await requireTenantSession();
  const status = readArchiveStatus((await searchParams).status);
  const [setting, credentials] = await Promise.all([
    workspaceFetch<{ mode: string }>("/v1/payment-setting"),
    workspaceFetch<{ toss_payments_credentials: Record<string, unknown>[] }>("/v1/toss-payments-credentials", { status }),
  ]);
  return <>
    <WorkspaceHeader title="도입사 설정" kicker="도입사 콘솔" description="결제 연동 키와 결제 모드를 설정하세요." />
    <div className="workspace-grid">
      <section className="workspace-panel"><h2>현재 결제 모드</h2>{setting.ok ? <><p><strong>{setting.data.mode === "test" ? "테스트 결제" : "실결제"}</strong></p><p className="muted">모드별 토스 키와 등록 카드를 사용합니다. API 키의 test 모드는 전체 데이터 샌드박스가 아닙니다.</p><div className="workspace-actions"><ActionPanel title="결제 모드 변경" operation="updatePaymentSetting" description="모드를 바꾸면 이후 요청에 사용하는 토스 키와 카드, 수납 조회 기준이 바뀝니다." fields={[{ name: "mode", label: "PG 결제 환경", hint: "실결제를 선택하면 실제 출금에 사용하는 키와 카드로 결제합니다. 화면의 시연 안내와 API 키 구분은 바뀌지 않습니다.", value: setting.data.mode, options: [{ value: "test", label: "테스트 결제" }, { value: "live", label: "실결제" }] }]} /></div></> : <WorkspaceError message={setting.error.message} />}</section>
    </div>
    <section className="workspace-panel"><div className="workspace-heading"><div><h2>토스페이먼츠 연동</h2><p className="field-hint">시크릿 키는 암호화해 저장하며 원문을 다시 조회할 수 없습니다. 브라우저 저장소와 URL에 넣지 않습니다.</p></div><ActionPanel title="토스 키 저장" operation="updateTossPaymentsCredential" fields={[{ name: "mode", label: "연동 모드", value: "test", options: [{ value: "test", label: "테스트" }, { value: "live", label: "실결제" }] }, { name: "client_key", label: "클라이언트 키", hint: "테스트는 test_ck_, 실결제는 live_ck_로 시작합니다." }, { name: "secret_key", label: "시크릿 키", type: "password", hint: "테스트는 test_sk_, 실결제는 live_sk_로 시작합니다." }]} /></div>{credentials.ok ? <WorkspaceTable rows={credentials.data.toss_payments_credentials.map(row => ({ ...row, id: `${row.mode}-${row.updated_at}-${row.archived_at ?? "active"}`, status: row.archived_at ? "archived" : "active" }))} rowKey="id" columns={[{ key: "mode", label: "PG 결제 환경" }, { key: "secret_key_last_four", label: "시크릿 키 끝 네 글자" }, { key: "verified_at", label: "확인 시각" }, { key: "updated_at", label: "저장 시각" }]} empty={status === "archived" ? "보관한 PG 키가 없습니다." : "테스트 키부터 등록하고 카드 등록과 결제를 확인하세요."} /> : <WorkspaceError message={credentials.error.message} />}</section>
    <section className="workspace-panel"><h2>청구 안내 메일</h2><p className="muted">메일 기록과 실제 발송은 다릅니다. 현재 화면에서는 메일 전달 완료를 보장하지 않습니다.</p></section>
  </>;
}
