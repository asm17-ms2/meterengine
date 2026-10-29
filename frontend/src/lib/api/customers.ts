import "server-only";
import type { SearchPage } from "@/lib/api/search";

import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, serverSend, type Result } from "@/lib/api/client";
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
          "개발 모드에서 강제한 에러 상태입니다. 사이드바의 표 상태 스위치를 정상으로 되돌리면 사라집니다.",
      },
    };
  }

  const result = await serverFetch<SearchPage<CustomerResponse>>(
    config.apiBaseUrl, "/v1/customers/search", toServerParams(query, CAPABILITIES.listCustomers),
  );
  return result.ok ? { ok: true, data: { ...result.data, customers: result.data.items, page: result.data.page - 1 } } : result;
}

export async function createCustomer(
  name: string,
  billingEmail: string | null,
  idempotencyKey?: string,
): Promise<Result<CustomerResponse>> {
  return serverSend<CustomerResponse>(config.apiBaseUrl, "/v1/customers", {
    idempotencyKey,
    method: "POST",
    body: { name, billing_email: billingEmail },
  });
}

export async function updateCustomer(
  id: string,
  name: string,
  billingEmail: string | null,
  version?: number,
): Promise<Result<CustomerResponse>> {
  return serverSend<CustomerResponse>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "PATCH",
    ifMatch: version === undefined ? undefined : `"${version}"`,
    body: { name, billing_email: billingEmail },
  });
}

export async function deleteCustomer(id: string): Promise<Result<void>> {
  return serverSend<void>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "DELETE",
  });
}
