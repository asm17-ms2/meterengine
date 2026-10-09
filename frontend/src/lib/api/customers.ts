import "server-only";

import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

export type ListCustomersResponse = {
  customers: CustomerResponse[];
};

export type CustomerResponse = {
  id: string;
  name: string;
  created_at: string;
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** 쿼리의 고객 id. UUID가 아니면 undefined로 두어 서버에 보내지 않는다. */
export function readCustomerId(raw: string | string[] | undefined): string | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value && UUID_PATTERN.test(value) ? value : undefined;
}

export async function listCustomers(
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

  return serverFetch<ListCustomersResponse>(config.apiBaseUrl, "/v1/customers");
}

export async function createCustomer(name: string): Promise<Result<CustomerResponse>> {
  return serverSend<CustomerResponse>(config.apiBaseUrl, "/v1/customers", {
    method: "POST",
    body: { name },
  });
}

export async function updateCustomer(
  id: string,
  name: string,
): Promise<Result<CustomerResponse>> {
  return serverSend<CustomerResponse>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "PUT",
    body: { name },
  });
}

export async function deleteCustomer(id: string): Promise<Result<void>> {
  return serverSend<void>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "DELETE",
  });
}
