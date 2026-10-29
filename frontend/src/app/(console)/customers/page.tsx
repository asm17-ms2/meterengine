import type { Metadata } from "next";
import { workspaceFetch } from "@/lib/api/workspace";
import { readArchiveStatus } from "@/lib/list-query";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { ArchiveFilter } from "@/components/workspace/ArchiveFilter";
import { WorkspaceTable, WorkspaceError } from "@/components/workspace/WorkspaceView";

type CustomerResponse = Record<string, unknown> & { id: string; name: string; external_id: string | null; archived_at?: string | null };

export const metadata: Metadata = { title: "고객" };

export default async function CustomersPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const query = params.q?.trim() ?? "";
  const status = readArchiveStatus(params.status);
  const result = await workspaceFetch<{ customers: CustomerResponse[] }>("/v1/customers", { status });
  const rows = result.ok ? result.data.customers.filter(customer => !query || `${customer.name} ${customer.external_id ?? ""}`.toLocaleLowerCase().includes(query.toLocaleLowerCase())).map(customer => ({ ...customer, status: customer.archived_at ? "archived" : "active" })) : [];
  return <>
    <WorkspaceHeader title="고객" kicker="도입사 콘솔" description="외부 고객 ID로 사용량을 연결하고 고객별 청구와 수납을 확인하세요. 보관한 고객의 과거 내역도 조회할 수 있습니다."><ActionPanel title="고객 등록" operation="createCustomer" description="외부 고객 ID는 도입사 시스템의 고유 ID입니다. 같은 ID로 다시 요청하면 기존 고객을 돌려줍니다." fields={[{ name: "name", label: "고객명", maxLength: 255 }, { name: "external_id", label: "외부 고객 ID", isOptional: true, placeholder: "customer-001" }, { name: "billing_email", label: "청구 이메일", type: "email", isOptional: true, maxLength: 254 }]} label="고객 저장" /></WorkspaceHeader>
    <section className="workspace-panel"><ArchiveFilter /><form method="get" className="workspace-actions"><input type="hidden" name="status" value={status} />{params.month && <input type="hidden" name="month" value={params.month} />}<label className="workspace-field"><span>이름 또는 외부 ID로 찾기</span><input name="q" defaultValue={query} placeholder="고객 검색" /></label><button className="month-apply" type="submit">검색</button></form>
      {result.ok ? <WorkspaceTable rows={rows} columns={[{ key: "name", label: "고객" }, { key: "status", label: "관리 상태", isStatus: true }, { key: "external_id", label: "외부 고객 ID" }, { key: "billing_email", label: "청구 이메일" }, { key: "created_at", label: "등록 시각", isDate: true }, { key: "archived_at", label: "보관 시각", isDate: true }]} rowActions={Object.fromEntries(rows.filter(customer => customer.archived_at).map(customer => [customer.id, [{ title: "고객 복원", operation: "restoreCustomer", description: `${customer.name} 고객의 신규 업무와 사용량 수집을 다시 허용합니다. 과거 기록은 그대로 유지됩니다.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: customer.id }] }]]))} empty={query ? "일치하는 고객이 없습니다. 이름이나 외부 ID를 바꾸거나 관리 상태를 확인하세요." : status === "archived" ? "보관한 고객이 없습니다." : status === "all" ? "등록된 고객이 없습니다. 첫 고객을 등록하세요." : "사용 중인 고객이 없습니다. 첫 고객을 등록하거나 보관 목록에서 복원하세요."} /> : <WorkspaceError message={result.error.message} />}
    </section>
  </>;
}
