import "server-only";

import { CAPABILITIES } from "@/lib/api/capabilities";
import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";
import { toServerParams, type ListQuery, type PaginationFields } from "@/lib/list-query";

export type ListCustomersResponse = PaginationFields & {
  customers: CustomerResponse[];
};

export type CustomerResponse = {
  archived_at?: string | null;
  id: string;
  name: string;
  billing_email: string | null;
  created_at: string;
};

export async function listCustomers(
  query: ListQuery,
  devState: DevState, _cacheTag?: string,
): Promise<Result<ListCustomersResponse>> {
  void _cacheTag;
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

  return serverFetch<ListCustomersResponse>(
    config.apiBaseUrl,
    "/v1/customers",
    toServerParams(query, CAPABILITIES.listCustomers),
  );
}

export async function createCustomer(
  name: string,
  billingEmail: string | null,
): Promise<Result<CustomerResponse>> {
  return serverSend<CustomerResponse>(config.apiBaseUrl, "/v1/customers", {
    method: "POST",
    body: { name, billing_email: billingEmail },
  });
}

export async function updateCustomer(
  id: string,
  name: string,
  billingEmail: string | null,
): Promise<Result<CustomerResponse>> {
  return serverSend<CustomerResponse>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "PUT",
    body: { name, billing_email: billingEmail },
  });
}

export async function deleteCustomer(id: string): Promise<Result<void>> {
  return serverSend<void>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "DELETE",
  });
}

export const CUSTOMERS_CACHE_TAG = "customers";
