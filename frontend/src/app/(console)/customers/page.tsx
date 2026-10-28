import type { Metadata } from "next";
import { listCustomers } from "@/lib/api/customers";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { readListQuery } from "@/lib/list-query";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceTable, WorkspaceError } from "@/components/workspace/WorkspaceView";
import { ListPagination } from "@/components/screen/ListPagination";
import { ResourceSearchControls } from "@/components/screen/ResourceSearchControls";
import { CsvDownload } from "@/components/screen/CsvDownload";
import { readMonth } from "@/lib/month";

export const metadata: Metadata = { title: "고객" };

export default async function CustomersPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const query = readListQuery(params, CAPABILITIES.listCustomers);
  const result = await listCustomers(query, query.devState);
  const rows = result.ok ? result.data.customers.map(customer => ({ ...customer, status: customer.archived_at ? "archived" : "active" })) : [];
  return <>
    <WorkspaceHeader title="고객" kicker="도입사 콘솔" description="외부 고객 ID로 사용량을 연결하고 고객별 청구와 수납을 확인하세요. 검색과 정렬은 보관 조건에 맞는 전체 고객에 적용됩니다."><ActionPanel title="고객 등록" operation="createCustomer" description="외부 고객 ID는 도입사 시스템의 고유 ID입니다. 같은 ID로 다시 요청하면 기존 고객을 돌려줍니다." fields={[{ name: "name", label: "고객명", maxLength: 255 }, { name: "external_id", label: "외부 고객 ID", isOptional: true, placeholder: "customer-001" }, { name: "billing_email", label: "청구 이메일", type: "email", isOptional: true, maxLength: 254 }]} label="고객 저장" /></WorkspaceHeader>
    <section className="workspace-panel">
      <ResourceSearchControls query={query} capabilities={CAPABILITIES.listCustomers} resource="customers" />
      <CsvDownload resource="customers" />
      {result.ok ? <>
        <p role="status">조건에 맞는 고객 {result.data.total ?? rows.length}곳, 검색 전 {result.data.total_unfiltered ?? result.data.total ?? rows.length}곳</p>
        <WorkspaceTable serverPaged rows={rows} columns={[{ key: "name", label: "고객", hrefPrefix: "/customers/", hrefSuffix: `?month=${month}` }, { key: "status", label: "관리 상태", isStatus: true }, { key: "external_id", label: "외부 고객 ID" }, { key: "billing_email", label: "청구 이메일" }, { key: "created_at", label: "등록 시각", isDate: true }, { key: "archived_at", label: "보관 시각", isDate: true }]} rowActions={Object.fromEntries(rows.filter(customer => customer.archived_at).map(customer => [customer.id, [{ title: "고객 복원", operation: "restoreCustomer", description: `${customer.name} 고객의 신규 업무와 사용량 수집을 다시 허용합니다. 과거 기록은 그대로 유지됩니다.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: customer.id }] }]]))} empty={query.q ? "일치하는 고객이 없습니다. 검색어나 관리 상태를 바꿔 주세요." : query.status === "archived" ? "보관한 고객이 없습니다." : "현재 조건의 고객이 없습니다. 조건을 바꾸거나 새 고객을 등록하세요."} />
        <ListPagination query={query} capabilities={CAPABILITIES.listCustomers} total={result.data.total} />
      </> : <WorkspaceError message={result.error.message} />}
    </section>
  </>;
}
