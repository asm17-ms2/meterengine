import type { Command } from "commander";
import type { SearchParams } from "@meterengine/sdk";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { UsageError } from "../exit-code.ts";
import { assertMonth } from "../month.ts";
import { assertCustomerId } from "../options.ts";

// 서버 검색 API의 size 상한이다(SearchParameters). 대부분 한 번에 다 받도록 기본값으로도 쓴다.
const MAX_PAGE_SIZE = 200;
const MAX_PAGE = 2147483647;
const POSITIVE_INTEGER = /^[1-9]\d*$/;

interface UsageOptions {
  month?: string;
  customer?: string;
  meter?: string;
  page?: string;
  size?: string;
}

function positiveInteger(value: string, max: number, code: string, message: string): number {
  if (!POSITIVE_INTEGER.test(value) || Number(value) > max) throw new UsageError(code, message);
  return Number(value);
}

// 서버에 보내기 전에 입력을 확인한다. 좁히지 않으면 undefined를 돌려 GET /v1/usage를 부른다.
function searchParamsOf(options: UsageOptions): SearchParams | undefined {
  if (options.customer !== undefined) assertCustomerId(options.customer);
  if (options.meter !== undefined && options.meter.trim() === "") throw new UsageError("invalid_meter", "--meter에는 미터 코드를 넣습니다");
  const isNarrowed = options.customer !== undefined || options.meter !== undefined;
  if (!isNarrowed) {
    if (options.page !== undefined || options.size !== undefined) {
      throw new UsageError("page_requires_filter", "--page와 --size는 --customer나 --meter와 함께 씁니다. 좁히지 않은 조회는 한 번에 전부 냅니다");
    }
    return undefined;
  }
  return {
    month: options.month,
    customer_id: options.customer,
    billable_metric_code: options.meter,
    page: options.page === undefined ? undefined : positiveInteger(options.page, MAX_PAGE, "invalid_page", "--page는 1 이상의 정수입니다"),
    size: options.size === undefined ? MAX_PAGE_SIZE : positiveInteger(options.size, MAX_PAGE_SIZE, "invalid_size", `--size는 1부터 ${MAX_PAGE_SIZE}까지의 정수입니다`),
  };
}

export function registerUsage(program: Command, deps: CliDeps): void {
  program
    .command("usage")
    .description("미터마다 고객별 사용량을 조회한다 (GET /v1/usage). --customer나 --meter로 좁히면 고객 단위 페이지로 낸다 (GET /v1/usage/search)")
    .option("--month <YYYY-MM>", "청구 월. 생략하면 이번 달(KST)")
    .option("--customer <uuid>", "MeterEngine 고객 UUID. 외부 ID는 받지 않으니 customers list --external-id로 UUID를 먼저 찾는다")
    .option("--meter <code>", "미터 코드. 그 달에 쓰지 않는 코드면 오류 없이 각 고객의 metrics가 빈 배열이고 summary.metric_count가 0이다")
    .option("--page <n>", "좁힌 조회의 쪽 번호. 기본 1")
    .option("--size <n>", `좁힌 조회의 한 쪽 고객 수. 1부터 ${MAX_PAGE_SIZE}까지, 기본 ${MAX_PAGE_SIZE}. total이 더 크면 --page로 다음 쪽을 받는다`)
    .action(async (options: UsageOptions, command: Command) => {
      if (options.month !== undefined) assertMonth(options.month);
      const search = searchParamsOf(options);
      const client = await clientFor(deps, command);
      const result = search === undefined
        ? await client.aggregateBillableMetricUsages(options.month === undefined ? {} : { month: options.month })
        : await client.searchUsage(search);
      printJson(deps.stdout, result);
    });
}
