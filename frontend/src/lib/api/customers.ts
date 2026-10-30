import "server-only";
import type { SearchPage } from "@/lib/api/search";

import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import { toServerParams, type ListQuery, type PaginationFields } from "@/lib/list-query";

export type ListCustomersResponse = PaginationFields & {
  summary?: Record<string, unknown>;
  customers: CustomerResponse[];
};

export type CustomerResponse = {
  archived_at?: string | null;
  id: string;
  external_id?: string | null;
  version: number;
  name: string;
  billing_email: string | null;
  created_at: string;
};

export async function listCustomers(
  query: ListQuery,
  devState: DevState,
): Promise<Result<ListCustomersResponse>> {
  if (devState === "empty") {
    return { ok: true, data: { customers: [] } };
  }
  if (devState === "error") {
    return {
      ok: false,
      error: {
        status: 503,
        code: "dev_forced",
        message:
          "개발 모드에서 강제한 에러 상태입니다. 주소의 state 파라미터를 지우면 사라집니다.",
      },
    };
  }

  const result = await serverFetch<SearchPage<CustomerResponse>>(
    config.apiBaseUrl, "/v1/customers/search", toServerParams(query, CAPABILITIES.listCustomers),
  );
  return result.ok ? { ok: true, data: { ...result.data, customers: result.data.items, page: result.data.page - 1 } } : result;
}
