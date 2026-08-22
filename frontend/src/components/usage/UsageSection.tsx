import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import { UsageTable, type UsageGroupView } from "@/components/usage/UsageTable";
import type { Result } from "@/lib/api/client";
import {
  countMeterLines,
  toCustomerGroups,
  type MonthlyUsage,
} from "@/lib/api/usage";
import { formatDecimal } from "@/lib/format";
import { shiftMonth } from "@/lib/month";

/**
 * 프라미스를 await하는 서버 컴포넌트. 페이지가 이걸 <Suspense>로 감싸서
 * 표 영역만 스켈레톤으로 바뀌고 화면 제목과 필터 행은 남는다.
 */
export async function UsageSection({
  usage,
  month,
}: {
  usage: Promise<Result<MonthlyUsage>>;
  month: string;
}) {
  const result = await usage;

  if (!result.ok) {
    return (
      <ErrorState
        title="집계를 불러오지 못했습니다"
        error={result.error}
        narrowerHref={`/usage?month=${shiftMonth(month, -1)}`}
      />
    );
  }

  const groups = toCustomerGroups(result.data);

  // 표가 비는 경로가 둘이고 운영자가 할 일이 다르다. 집계 라인은 미터 x 고객이라
  // 어느 한쪽이 0이면 라인이 0인데, 원인을 뭉뚱그리면 멀쩡한 쪽 설정 화면으로
  // 보내게 된다. 수집된 이벤트 유무는 둘 다와 무관하다. 백엔드가 이벤트 0건인
  // 고객도 수량 0으로 남기기 때문이다.
  if (result.data.metrics.length === 0) {
    return (
      <EmptyState
        title="등록된 미터가 없습니다"
        body={`이 도입사에 등록된 미터가 없어 ${month} 집계 라인이 만들어지지 않았습니다. 미터 설정을 확인하세요.`}
        resetHref="/usage"
      />
    );
  }

  if (groups.length === 0) {
    // 미터는 있는데 고객이 0명이다. 미터마다 customers가 빈 배열로 온다
    // (UsageAggregationService.aggregate가 고객 목록을 훑어 만들기 때문).
    return (
      <EmptyState
        title="등록된 고객이 없습니다"
        body={`미터 ${result.data.metrics.length}개가 등록돼 있지만 이 도입사에 고객이 없어 ${month} 집계 라인이 만들어지지 않았습니다. 고객을 먼저 등록하세요.`}
        resetHref="/usage"
      />
    );
  }

  const view: UsageGroupView[] = groups.map((group) => ({
    customerId: group.customerId,
    customerName: group.customerName,
    meters: group.meters.map((meter) => ({
      label: meter.label,
      quantity: formatDecimal(meter.quantity),
    })),
  }));

  return (
    <>
      <UsageTable groups={view} />
      <div className="screen-footer">
        <p className="screen-note">
          사용량이 0인 미터와 이벤트가 0건인 고객도 0으로 표시됩니다. 계약된
          미터는 값이 없어도 라인이 유지됩니다.
        </p>
      </div>
    </>
  );
}

/** 화면 제목 오른쪽 메타. 같은 프라미스를 보되 Suspense 경계가 따로다. */
export async function UsageMeta({
  usage,
}: {
  usage: Promise<Result<MonthlyUsage>>;
}) {
  const result = await usage;
  if (!result.ok) return null;

  const groups = toCustomerGroups(result.data);
  return (
    <>
      고객 <b>{groups.length}</b>곳, 미터 라인{" "}
      <b>{countMeterLines(groups)}</b>줄
    </>
  );
}
