import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

/**
 * GET /v1/usage 응답. 백엔드의 ListBillableMetricUsagesResponse와 1:1이다.
 *
 * 응답은 미터 중심(미터 안에 고객)인데 화면은 고객 중심(고객 안에 미터)이다.
 * 뒤집는 것은 toCustomerGroups가 한다.
 */
export type ListBillableMetricUsagesResponse = {
  month: string;
  billable_metric_usages: BillableMetricUsageResponse[];
};

export type BillableMetricUsageResponse = {
  code: string;
  name: string;
  event_type: string;
  aggregation: string;
  target_property: string | null;
  customers: BillableMetricUsageCustomer[];
};

export type BillableMetricUsageCustomer = {
  customer_id: string;
  customer_name: string;
  /**
   * 백엔드는 BigDecimal로 계산하지만 JSON에는 따옴표 없는 수로 나온다.
   * 소수를 쓰는 미터는 이미 있다 (network-egress, 시드에 단가 120원까지 붙어
   * 있다). 그래서 이 값은 JS number(double)를 거치고, 유효숫자 약 16자리를
   * 넘으면 값 자체가 어긋난다. 문자열 직렬화로 바꾸는 것은 아직 미해결이다.
   */
  quantity: number;
};

/** 화면이 그리는 단위. 고객 하나가 그룹 행, 그 아래 미터마다 자식 행 하나. */
export type CustomerGroup = {
  customerId: string;
  customerName: string;
  billableMetricLines: BillableMetricLine[];
};

export type BillableMetricLine = {
  /** `token-usage (token)` 형태. 디자인의 미터 라인 표기다. */
  label: string;
  quantity: number;
};

/**
 * 미터 중심 응답을 고객 중심 트리로 뒤집는다.
 *
 * 백엔드가 이미 정렬해서 준다. 고객은 이름/ID 오름차순, 미터는 code 오름차순이고,
 * 모든 미터가 도입사의 전체 고객을 담는다 (이벤트가 없으면 quantity 0). 그래서
 * 첫 미터의 고객 순서를 그대로 쓰면 정렬이 유지되고, 여기서 다시 정렬하지 않는다.
 */
export function toCustomerGroups(usage: ListBillableMetricUsagesResponse): CustomerGroup[] {
  const groups = new Map<string, CustomerGroup>();

  for (const billableMetricUsage of usage.billable_metric_usages) {
    const label = billableMetricUsage.target_property
      ? `${billableMetricUsage.code} (${billableMetricUsage.target_property})`
      : billableMetricUsage.code;

    for (const customer of billableMetricUsage.customers) {
      let group = groups.get(customer.customer_id);
      if (!group) {
        group = {
          customerId: customer.customer_id,
          customerName: customer.customer_name,
          billableMetricLines: [],
        };
        groups.set(customer.customer_id, group);
      }
      group.billableMetricLines.push({ label, quantity: customer.quantity });
    }
  }

  return [...groups.values()];
}

/** 표에 그려질 미터 라인 총 개수. 화면 헤더의 '미터 라인 N줄'이다. */
export function countBillableMetricLines(groups: CustomerGroup[]): number {
  return groups.reduce((sum, group) => sum + group.billableMetricLines.length, 0);
}

/**
 * 개발 모드 상태 스위치는 네트워크 호출 전에 갈린다. 프로덕션에서는
 * devState가 항상 'normal'이라 이 분기가 통째로 제거된다.
 *
 * 'loading'은 여기 오지 않는다. 페이지가 로더를 부르지 않고 스켈레톤으로 단락한다.
 * 영원히 resolve되지 않는 프라미스를 만들면 SSR 응답이 멈춘다.
 */
export async function loadUsage(
  month: string,
  devState: DevState,
): Promise<Result<ListBillableMetricUsagesResponse>> {
  if (devState === "empty") {
    return { ok: true, data: { month, billable_metric_usages: [] } };
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
  return serverFetch<ListBillableMetricUsagesResponse>(config.apiBaseUrl, "/v1/usage", { month });
}
