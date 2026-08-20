import "server-only";

import { serverFetch, serverSend, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

/**
 * GET /v1/customers 응답. 백엔드의 CustomerListResponse(MS2-155)와 1:1이다.
 *
 * 페이지를 나누지 않는다. 이 도입사의 고객 전부가 응답의 정의이고 total도 없다.
 * 그래서 화면에도 페이지 나누기가 없다 (디자인의 페이저는 프로토타입이 1페이지로
 * 고정해 둔 자리표시자였다).
 */
export type CustomerList = {
  customers: CustomerEntry[];
};

export type CustomerEntry = {
  customer_id: string;
  name: string;
  /**
   * ISO 8601. 서버가 행을 만든 시각을 DB가 찍는다 (MS2-171).
   *
   * V3 마이그레이션 이전에 있던 고객은 마이그레이션 시각을 나눠 받았다. 그 값은
   * 실제 등록 시각이 아니고, 값이 전부 같다는 것이 백필 표식이다. 화면은 이것을
   * 구별해 표시하지 않는다 - 구별할 근거가 응답에 없다.
   */
  created_at: string;
};

/** POST /v1/customers, PUT /v1/customers/{id} 응답. 목록 항목과 같은 레코드다. */
export type Customer = CustomerEntry;

/**
 * 개발 모드 상태 스위치는 네트워크 호출 전에 갈린다 (loadEvents와 같은 구조).
 * 'loading'은 여기 오지 않는다. 페이지가 로더를 부르지 않고 스켈레톤으로 단락한다.
 */
export async function loadCustomers(
  devState: DevState,
): Promise<Result<CustomerList>> {
  if (devState === "empty") {
    return { ok: true, data: { customers: [] } };
  }
  if (devState === "error") {
    return {
      ok: false,
      error: {
        status: 503,
        code: "dev_forced",
        title: "개발 모드에서 강제한 에러 상태입니다",
        detail: "사이드바의 표 상태 스위치를 정상으로 되돌리면 사라집니다.",
      },
    };
  }

  return serverFetch<CustomerList>(config.apiBaseUrl, "/v1/customers");
}

/** 이름 하나만 보낸다. 백엔드의 SaveCustomerRequest와 같은 모양이다. */
export async function createCustomer(name: string): Promise<Result<Customer>> {
  return serverSend<Customer>(config.apiBaseUrl, "/v1/customers", {
    method: "POST",
    body: { name },
  });
}

/**
 * 이름을 고친다. PUT인 이유는 고칠 수 있는 것이 이름 하나이고 그것이 항상 필수라
 * 부분 갱신이라 부를 것이 없어서다 (백엔드 결정, openapi.yaml).
 */
export async function renameCustomer(
  id: string,
  name: string,
): Promise<Result<Customer>> {
  return serverSend<Customer>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "PUT",
    body: { name },
  });
}

/**
 * 지운다. 성공은 204라 본문이 없다.
 *
 * 사용량 이벤트가 한 건이라도 있으면 409(customer_has_events)로 거절된다. 이벤트가
 * 청구 근거라, 고객만 지우면 그 사용량이 어느 청구서에도 오르지 않기 때문이다.
 * 화면은 그 거절을 오류가 아니라 안내 다이얼로그로 보여준다.
 */
export async function deleteCustomer(id: string): Promise<Result<void>> {
  return serverSend<void>(config.apiBaseUrl, `/v1/customers/${id}`, {
    method: "DELETE",
  });
}
