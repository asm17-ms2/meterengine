import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

/**
 * GET /v1/invoice 응답. 백엔드의 DraftInvoiceResponse와 1:1이다.
 *
 * /v1/usage와 달리 응답이 이미 고객 중심(고객 안에 라인)이라 뒤집을 것이 없다.
 * 금액 계산은 전부 서버 몫이다. 라인별 절사 규칙이 서버에 있으므로 화면은
 * amount와 total_amount를 그대로 그린다 (MS2-127 인수 조건).
 */
export type DraftInvoiceResponse = {
  month: string;
  calculated_at: string;
  total_amount: number;
  customers: DraftInvoiceCustomer[];
};

export type DraftInvoiceCustomer = {
  customer_id: string;
  customer_name: string;
  /** 고객 소계. 라인 amount의 합을 서버가 계산해 준다. 정수 KRW다. */
  amount: number;
  lines: DraftInvoiceLine[];
};

export type DraftInvoiceLine = {
  billable_metric_code: string;
  target_property: string | null;
  /** 소수가 올 수 있다 (BigDecimal 직렬화, usage.ts의 quantity 주석 참조). */
  quantity: number;
  /** 소수가 올 수 있다. 시드의 token-usage 단가가 0.007이다. */
  unit_price: number;
  /** 라인별 절사(내림)가 끝난 정수 KRW. */
  amount: number;
};

/** 표에 그려질 청구 라인 총 개수. 화면 헤더의 '청구 라인 N줄'이다. */
export function countDraftInvoiceLines(customers: DraftInvoiceCustomer[]): number {
  return customers.reduce((sum, customer) => sum + customer.lines.length, 0);
}

/**
 * 개발 모드 상태 스위치는 네트워크 호출 전에 갈린다. 프로덕션에서는
 * devState가 항상 'normal'이라 이 분기가 통째로 제거된다.
 *
 * 'loading'은 여기 오지 않는다. 페이지가 로더를 부르지 않고 스켈레톤으로 단락한다.
 * 영원히 resolve되지 않는 프라미스를 만들면 SSR 응답이 멈춘다.
 */
export async function loadDraftInvoice(
  month: string,
  devState: DevState,
): Promise<Result<DraftInvoiceResponse>> {
  if (devState === "empty") {
    return {
      ok: true,
      data: {
        month,
        calculated_at: new Date().toISOString(),
        total_amount: 0,
        customers: [],
      },
    };
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
  return serverFetch<DraftInvoiceResponse>(config.apiBaseUrl, "/v1/invoice", { month });
}
