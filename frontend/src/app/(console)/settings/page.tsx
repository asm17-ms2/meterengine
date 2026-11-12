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
  const supplierDescription = environment === "legacy" ? "이 환경의 인보이스에 쓰는 공급자 정보예요." : `${ENVIRONMENT_LABELS[environment]}의 청구에만 쓰는 공급자 정보예요. 다른 환경의 정보는 바뀌지 않아요.`;
  const [supplier, setting, credentials] = await Promise.all([
    workspaceFetch<SupplierResponse>("/v1/organization/supplier"),
    workspaceFetch<{ mode: string }>("/v1/payment-setting"),
    workspaceFetch<{ toss_payments_credentials: Record<string, unknown>[] }>("/v1/toss-payments-credentials", { status }),
  ]);
  return <>
    <WorkspaceHeader title="조직 설정" description="청구에 쓸 공급자 정보와 결제 연동을 준비하세요." />
    <div className="workspace-grid">
      {supplier.ok ? canManage ? <ActionPanel isInline title="공급자 정보" operation="updateSupplier" description={supplierDescription} label="공급자 정보 저장" fields={[{ name: "business_registration_number", label: "사업자등록번호", value: supplier.data.business_registration_number ?? "", hint: "숫자 10자리예요. 하이픈(-)은 빼고 저장해요." }, { name: "representative_name", label: "대표자명", value: supplier.data.representative_name ?? "" }, { name: "business_address", label: "사업장 주소", value: supplier.data.business_address ?? "" }]} /> : <section className="workspace-panel"><h2>공급자 정보</h2><p className="muted">{supplierDescription}</p><dl className="detail-strip"><div><dt>사업자등록번호</dt><dd>{supplier.data.business_registration_number || "미등록"}</dd></div><div><dt>대표자명</dt><dd>{supplier.data.representative_name || "미등록"}</dd></div><div><dt>사업장 주소</dt><dd>{supplier.data.business_address || "미등록"}</dd></div></dl></section> : <WorkspaceError message={supplier.error.message} status={supplier.error.status} />}
      <section className="workspace-panel"><h2>현재 결제 모드</h2>{setting.ok ? <><p><strong>{setting.data.mode === "test" ? "테스트 결제" : "실결제"}</strong></p>{environment === "legacy" ? <><p className="muted">결제 모드에 따라 쓰는 토스페이먼츠 키와 등록 카드가 달라요.</p><div className="workspace-actions"><ActionPanel title="결제 모드 변경" operation="updatePaymentSetting" description="바꾸면 이후 요청에 쓰는 토스페이먼츠 키와 등록 카드, 수납 조회 기준이 함께 바뀌어요." fields={[{ name: "mode", label: "결제 모드", hint: "실결제를 고르면 실제로 출금되는 키와 카드로 결제해요.", value: setting.data.mode, options: [{ value: "test", label: "테스트 결제" }, { value: "live", label: "실결제" }] }]} /></div></> : <p className="muted">{ENVIRONMENT_LABELS[environment]}의 카드와 토스페이먼츠 키만 써요. 결제 모드를 바꾸려면 화면 위에서 업무 환경을 바꾸세요.</p>}</> : <WorkspaceError message={setting.error.message} status={setting.error.status} />}</section>
    </div>
    <section className="workspace-panel"><div className="workspace-heading"><div><h2>토스페이먼츠 연동</h2><p className="field-hint">시크릿 키는 암호화해 저장해서 나중에 다시 볼 수 없어요. 브라우저 저장소와 URL에도 남기지 않아요.</p>{environment !== "legacy" && <p className="field-hint">{ENVIRONMENT_LABELS[environment]}의 토스페이먼츠 키를 등록해요.</p>}</div><ActionPanel title="토스페이먼츠 키 저장" operation="updateTossPaymentsCredential" fields={[...(environment === "legacy" ? [{ name: "mode", label: "결제 모드", value: "test", options: [{ value: "test", label: "테스트 결제" }, { value: "live", label: "실결제" }] }] : [{ name: "mode", label: "", type: "hidden" as const, value: environment }]), { name: "client_key", label: "클라이언트 키", hint: "테스트는 test_ck_, 실결제는 live_ck_로 시작해요." }, { name: "secret_key", label: "시크릿 키", type: "password", hint: "테스트는 test_sk_, 실결제는 live_sk_로 시작해요." }]} /></div><ArchiveFilter label="토스페이먼츠 키 관리 상태" />{credentials.ok ? <WorkspaceTable label="토스페이먼츠 키 목록" rows={credentials.data.toss_payments_credentials.map(row => ({ ...row, id: `${row.mode}-${row.updated_at}-${row.archived_at ?? "active"}`, status: row.archived_at ? "archived" : "active" }))} rowKey="id" columns={[{ key: "mode", label: "결제 모드" }, { key: "status", label: "관리 상태", isStatus: true }, { key: "secret_key_last_four", label: "시크릿 키 끝 네 글자" }, { key: "verified_at", label: "확인 시각" }, { key: "updated_at", label: "저장 시각" }, { key: "archived_at", label: "보관 시각", isDate: true }]} rowActions={Object.fromEntries(credentials.data.toss_payments_credentials.filter(row => !row.archived_at).map(row => [`${row.mode}-${row.updated_at}-${row.archived_at ?? "active"}`, [{ title: "토스페이먼츠 키 보관", operation: "archiveTossPaymentsCredential", description: `보관하면 ${row.mode === "test" ? "테스트 결제" : "실결제"} 토스페이먼츠 키로 새 결제와 카드 등록을 할 수 없어요. 진행 중인 결제의 확인과 취소에는 보관한 키를 계속 써요. 다시 쓰려면 키를 새로 저장하세요.`, fields: [{ name: "mode", label: "", type: "hidden" as const, value: String(row.mode) }], isDanger: true }]]))} empty={status === "archived" ? "보관한 토스페이먼츠 키가 없어요." : "이 환경의 토스페이먼츠 키를 등록하고 카드 등록과 결제를 확인하세요."} /> : <WorkspaceError message={credentials.error.message} status={credentials.error.status} />}</section>
    <section className="workspace-panel"><h2>청구 안내 메일</h2><p className="muted">인보이스를 확정하면 고객의 청구 이메일로 보낼 메일을 기록만 하고, 실제로 보내지는 않아요. 여기서 바꿀 설정은 없어요.</p></section>
  </>;
}
