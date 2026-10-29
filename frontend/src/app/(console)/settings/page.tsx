import { requireTenantSession } from "@/lib/auth/session";
import { hasScope } from "@/lib/auth/permissions";
import { ArchiveFilter } from "@/components/workspace/ArchiveFilter";
import { readArchiveStatus } from "@/lib/list-query";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { ENVIRONMENT_LABELS, readEnvironment } from "@/lib/environment";

type SupplierResponse = { business_registration_number: string | null; representative_name: string | null; business_address: string | null };

export default async function SettingsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const session = await requireTenantSession();
  const canManage = hasScope(session.scopes, "admin");
  const params = await searchParams;
  const status = readArchiveStatus(params.status);
  const environment = readEnvironment(params.environment);
  const supplierDescription = environment === "legacy" ? "기존 데이터 인보이스에 사용할 공급자 정보입니다." : `${ENVIRONMENT_LABELS[environment]}의 청구에만 사용하는 공급자 정보입니다. 다른 환경의 정보는 바뀌지 않습니다.`;
  const [supplier, setting, credentials] = await Promise.all([
    workspaceFetch<SupplierResponse>("/v1/organization/supplier"),
    workspaceFetch<{ mode: string }>("/v1/payment-setting"),
    workspaceFetch<{ toss_payments_credentials: Record<string, unknown>[] }>("/v1/toss-payments-credentials", { status }),
  ]);
  return <>
    <WorkspaceHeader title="도입사 설정" kicker="도입사 콘솔" description="청구에 사용할 공급자 정보와 결제 연동을 준비하세요." />
    <div className="workspace-grid">
      {supplier.ok ? canManage ? <ActionPanel isInline title="공급자 정보" operation="updateSupplier" description={supplierDescription} label="공급자 정보 저장" fields={[{ name: "business_registration_number", label: "사업자등록번호", value: supplier.data.business_registration_number ?? "", hint: "숫자 10자리입니다. 하이픈(-)은 빼고 저장합니다." }, { name: "representative_name", label: "대표자명", value: supplier.data.representative_name ?? "" }, { name: "business_address", label: "사업장 주소", value: supplier.data.business_address ?? "" }]} /> : <section className="workspace-panel"><h2>공급자 정보</h2><p className="muted">{supplierDescription}</p><dl><dt>사업자등록번호</dt><dd>{supplier.data.business_registration_number || "미등록"}</dd><dt>대표자명</dt><dd>{supplier.data.representative_name || "미등록"}</dd><dt>사업장 주소</dt><dd>{supplier.data.business_address || "미등록"}</dd></dl></section> : <WorkspaceError message={supplier.error.message} />}
      <section className="workspace-panel"><h2>현재 결제 환경</h2>{setting.ok ? <><p><strong>{setting.data.mode === "test" ? "테스트 결제" : "실결제"}</strong></p>{environment === "legacy" ? <><p className="muted">기존 데이터의 결제 모드입니다. 모드별 토스 키와 등록 카드를 사용합니다.</p><div className="workspace-actions"><ActionPanel title="결제 모드 변경" operation="updatePaymentSetting" description="기존 데이터의 이후 요청에 사용하는 토스 키와 카드, 수납 조회 기준이 바뀝니다." fields={[{ name: "mode", label: "PG 결제 환경", hint: "실결제를 선택하면 실제 출금에 사용하는 키와 카드로 결제합니다.", value: setting.data.mode, options: [{ value: "test", label: "테스트 결제" }, { value: "live", label: "실결제" }] }]} /></div></> : <p className="muted">{ENVIRONMENT_LABELS[environment]}의 카드와 토스 키만 사용합니다. 결제 환경을 바꾸려면 상단 업무 환경을 전환하세요.</p>}</> : <WorkspaceError message={setting.error.message} />}</section>
    </div>
    <section className="workspace-panel"><div className="workspace-heading"><div><h2>토스페이먼츠 연동</h2><p className="field-hint">시크릿 키는 암호화해 저장하며 원문을 다시 조회할 수 없습니다. 브라우저 저장소와 URL에 넣지 않습니다.</p>{environment !== "legacy" && <p className="field-hint">{ENVIRONMENT_LABELS[environment]}의 토스 키를 등록합니다.</p>}</div><ActionPanel title="토스 키 저장" operation="updateTossPaymentsCredential" fields={[...(environment === "legacy" ? [{ name: "mode", label: "연동 모드", value: "test", options: [{ value: "test", label: "테스트" }, { value: "live", label: "실결제" }] }] : [{ name: "mode", label: "", type: "hidden" as const, value: environment }]), { name: "client_key", label: "클라이언트 키", hint: "테스트는 test_ck_, 실결제는 live_ck_로 시작합니다." }, { name: "secret_key", label: "시크릿 키", type: "password", hint: "테스트는 test_sk_, 실결제는 live_sk_로 시작합니다." }]} /></div><ArchiveFilter label="PG 키 관리 상태" />{credentials.ok ? <WorkspaceTable rows={credentials.data.toss_payments_credentials.map(row => ({ ...row, id: `${row.mode}-${row.updated_at}-${row.archived_at ?? "active"}`, status: row.archived_at ? "archived" : "active" }))} rowKey="id" columns={[{ key: "mode", label: "PG 결제 환경" }, { key: "status", label: "관리 상태", isStatus: true }, { key: "secret_key_last_four", label: "시크릿 키 끝 네 글자" }, { key: "verified_at", label: "확인 시각" }, { key: "updated_at", label: "저장 시각" }, { key: "archived_at", label: "보관 시각", isDate: true }]} rowActions={Object.fromEntries(credentials.data.toss_payments_credentials.filter(row => !row.archived_at).map(row => [`${row.mode}-${row.updated_at}-${row.archived_at ?? "active"}`, [{ title: "PG 키 보관", operation: "archiveTossPaymentsCredential", description: `${row.mode === "test" ? "테스트" : "실결제"} PG 키로 새 결제와 카드 등록을 중단합니다. 진행 중 결제 확인과 취소에는 보존한 키를 사용합니다. 다시 사용하려면 키를 재등록하세요.`, fields: [{ name: "mode", label: "", type: "hidden" as const, value: String(row.mode) }], isDanger: true }]]))} empty={status === "archived" ? "보관한 PG 키가 없습니다." : "현재 환경의 토스 키를 등록하고 카드 등록과 결제를 확인하세요."} /> : <WorkspaceError message={credentials.error.message} />}</section>
    <section className="workspace-panel"><h2>청구 안내 메일</h2><p className="muted">인보이스를 확정하면 고객의 청구 이메일로 보낼 청구 메일을 기록만 하고 실제로 발송하지 않습니다. 이 화면에서 바꿀 설정은 없습니다.</p></section>
  </>;
}
