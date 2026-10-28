import { requireTenantSession } from "@/lib/auth/session";
import { hasScope } from "@/lib/auth/permissions";
import { readArchiveStatus } from "@/lib/list-query";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";

type SupplierResponse = { business_registration_number: string | null; representative_name: string | null; business_address: string | null };

export default async function SettingsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const session = await requireTenantSession();
  const canManage = hasScope(session.scopes, "admin");
  const status = readArchiveStatus((await searchParams).status);
  const [supplier, setting, credentials] = await Promise.all([
    workspaceFetch<SupplierResponse>("/v1/organization/supplier"),
    workspaceFetch<{ mode: string }>("/v1/payment-setting"),
    workspaceFetch<{ toss_payments_credentials: Record<string, unknown>[] }>("/v1/toss-payments-credentials", { status }),
  ]);
  return <>
    <WorkspaceHeader title="도입사 설정" kicker="도입사 콘솔" description="청구에 사용할 공급자 정보와 결제 연동을 준비하세요." />
    <div className="workspace-grid">
      {supplier.ok ? canManage ? <ActionPanel isInline title="공급자 정보" operation="updateSupplier" description="인보이스를 발행하는 도입사의 사업자 정보입니다." label="공급자 정보 저장" fields={[{ name: "business_registration_number", label: "사업자등록번호", value: supplier.data.business_registration_number ?? "", hint: "숫자 10자리입니다. 하이픈(-)은 빼고 저장합니다." }, { name: "representative_name", label: "대표자명", value: supplier.data.representative_name ?? "" }, { name: "business_address", label: "사업장 주소", value: supplier.data.business_address ?? "" }]} /> : <section className="workspace-panel"><h2>공급자 정보</h2><p className="muted">인보이스를 발행하는 도입사의 사업자 정보입니다.</p><dl><dt>사업자등록번호</dt><dd>{supplier.data.business_registration_number || "미등록"}</dd><dt>대표자명</dt><dd>{supplier.data.representative_name || "미등록"}</dd><dt>사업장 주소</dt><dd>{supplier.data.business_address || "미등록"}</dd></dl></section> : <WorkspaceError message={supplier.error.message} />}
      <section className="workspace-panel"><h2>현재 결제 모드</h2>{setting.ok ? <><p><strong>{setting.data.mode === "test" ? "테스트 결제" : "실결제"}</strong></p><p className="muted">모드별 토스 키와 등록 카드를 사용합니다. API 키의 test 모드는 전체 데이터 샌드박스가 아닙니다.</p><ActionPanel title="결제 모드 변경" operation="updatePaymentSetting" description="모드를 바꾸면 이후 요청에 사용하는 토스 키와 카드, 수납 조회 기준이 바뀝니다." fields={[{ name: "mode", label: "PG 결제 환경", hint: "실결제를 선택하면 실제 출금에 사용하는 키와 카드로 결제합니다. 화면의 시연 안내와 API 키 구분은 바뀌지 않습니다.", value: setting.data.mode, options: [{ value: "test", label: "테스트 결제" }, { value: "live", label: "실결제" }] }]} /></> : <WorkspaceError message={setting.error.message} />}</section>
    </div>
    <section className="workspace-panel"><div className="workspace-heading"><div><h2>토스페이먼츠 연동</h2><p className="field-hint">시크릿 키는 암호화해 저장하며 원문을 다시 조회할 수 없습니다. 브라우저 저장소와 URL에 넣지 않습니다.</p></div><ActionPanel title="토스 키 저장" operation="updateTossPaymentsCredential" fields={[{ name: "mode", label: "연동 모드", value: "test", options: [{ value: "test", label: "테스트" }, { value: "live", label: "실결제" }] }, { name: "client_key", label: "클라이언트 키", type: "password" }, { name: "secret_key", label: "시크릿 키", type: "password" }]} /></div>{credentials.ok ? <WorkspaceTable rows={credentials.data.toss_payments_credentials.map(row => ({ ...row, id: `${row.mode}-${row.updated_at}-${row.archived_at ?? "active"}`, status: row.archived_at ? "archived" : "active" }))} rowKey="id" columns={[{ key: "mode", label: "PG 결제 환경" }, { key: "secret_key_last_four", label: "시크릿 키 끝 네 글자" }, { key: "verified_at", label: "확인 시각" }, { key: "updated_at", label: "저장 시각" }]} empty={status === "archived" ? "보관한 PG 키가 없습니다." : "테스트 키부터 등록하고 카드 등록과 결제를 확인하세요."} /> : <WorkspaceError message={credentials.error.message} />}</section>
    <section className="workspace-panel"><h2>청구 안내 메일</h2><p className="muted">메일 기록과 실제 발송은 다릅니다. 현재 화면에서는 메일 전달 완료를 보장하지 않습니다.</p></section>
  </>;
}
