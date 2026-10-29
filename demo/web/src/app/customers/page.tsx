import { Alert } from "antd";
import { connection } from "next/server";

import { CustomerPortal } from "@/components/CustomerPortal";
import { currentBillingMonth, isBillingMonth, loadCustomerPortal } from "@/lib/customer-portal";
import { clientFor, targetNames, toErrorView } from "@/lib/meter-engine";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

async function loadPortalProps(params: Record<string, string | string[] | undefined>) {
  try {
    const names = targetNames();
    const target = typeof params.target === "string" && names.includes(params.target) ? params.target : names[0];
    if (target === undefined) throw new Error("연동 대상이 없습니다. 서버의 METERENGINE_TARGETS 설정을 확인하세요.");
    const month = typeof params.month === "string" && isBillingMonth(params.month) ? params.month : currentBillingMonth();
    const client = clientFor(target, { maxRetries: 0, timeoutMs: 5000 });
    const response = await client.listCustomers();
    const selected = typeof params.customer_id === "string" && params.customer_id !== "" ? params.customer_id : response.customers[0]?.id;
    if (selected !== undefined && !response.customers.some((customer) => customer.id === selected)) {
      throw new Error("도입사에 속한 고객을 다시 선택하세요.");
    }
    const data = selected === undefined ? null : await loadCustomerPortal(client, selected, month);
    return { props: { names, target, customers: response.customers, month, data, isOperatorView: params.view === "operator" } };
  } catch (error) {
    return { error: toErrorView(error).message };
  }
}

export default async function CustomersPage({ searchParams }: { searchParams: SearchParams }) {
  await connection();
  const loaded = await loadPortalProps(await searchParams);
  if (loaded.props === undefined) return <Alert type="error" title="고객 화면을 불러오지 못했습니다" description={loaded.error} action={<a href="/customers">다시 불러오기</a>} />;
  return <CustomerPortal {...loaded.props} />;
}
