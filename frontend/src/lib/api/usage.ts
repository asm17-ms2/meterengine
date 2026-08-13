import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { DevState } from "@/lib/dev-state";

/**
 * GET /v1/usage 응답. 백엔드의 MonthlyUsageResponse와 1:1이다.
 *
 * 응답은 미터 중심(미터 안에 고객)인데 화면은 고객 중심(고객 안에 미터)이다.
 * 뒤집는 것은 toCustomerGroups가 한다.
 *
 * 금액은 없다. unit_price를 어떤 엔드포인트도 내려주지 않고, 화면에서 금액을
 * 계산하지 않는다 (백엔드 MonthlyUsageResponse javadoc 참조). 금액은 MS2-127에서
 * 청구 예정액 API로 받는다.
 */
export type MonthlyUsage = {
  month: string;
  metrics: MetricEntry[];
};

export type MetricEntry = {
  code: string;
  name: string;
  event_type: string;
  aggregation: string;
  target_property: string | null;
  customers: CustomerEntry[];
};

export type CustomerEntry = {
  customer_id: string;
  customer_name: string;
  /**
   * 백엔드는 BigDecimal로 계산하지만 JSON에는 따옴표 없는 수로 나온다.
   * 토큰 같은 정수 미터는 안전하다. 소수를 쓰는 미터가 생기면 문자열 직렬화로
   * 바꾸자고 백엔드에 요청해야 한다.
   */
  quantity: number;
};

/** 화면이 그리는 단위. 고객 하나가 그룹 행, 그 아래 미터마다 자식 행 하나. */
export type CustomerGroup = {
  customerId: string;
  customerName: string;
  meters: MeterLine[];
};

export type MeterLine = {
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
export function toCustomerGroups(usage: MonthlyUsage): CustomerGroup[] {
  const groups = new Map<string, CustomerGroup>();

  for (const metric of usage.metrics) {
    const label = metric.target_property
      ? `${metric.code} (${metric.target_property})`
      : metric.code;

    for (const customer of metric.customers) {
      let group = groups.get(customer.customer_id);
      if (!group) {
        group = {
          customerId: customer.customer_id,
          customerName: customer.customer_name,
          meters: [],
        };
        groups.set(customer.customer_id, group);
      }
      group.meters.push({ label, quantity: customer.quantity });
    }
  }

  return [...groups.values()];
}

/** 표에 그려질 미터 라인 총 개수. 화면 헤더의 '미터 라인 N줄'이다. */
export function countMeterLines(groups: CustomerGroup[]): number {
  return groups.reduce((sum, group) => sum + group.meters.length, 0);
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
): Promise<Result<MonthlyUsage>> {
  if (devState === "empty") {
    return { ok: true, data: { month, metrics: [] } };
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
  return serverFetch<MonthlyUsage>(config.apiBaseUrl, "/v1/usage", { month });
}
