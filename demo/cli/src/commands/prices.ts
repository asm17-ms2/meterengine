import type { Command } from "commander";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { assertMonth } from "../month.ts";
import { assertCustomerId } from "../options.ts";

export function registerPrices(program: Command, deps: CliDeps): void {
  const prices = program.command("prices").description("과금 미터별 단가");
  prices
    .command("list")
    .description("그 달에 유효한 과금 미터별 단가를 조회한다 (GET /v1/billable-metric-prices)")
    .option("--month <YYYY-MM>", "청구 월. 생략하면 이번 달(KST)")
    .option("--customer <uuid>", "이 고객 전용 단가만 본다(조직 공통 단가는 빠진다). 생략하면 조직 공통 단가만")
    .action(async (options: { month?: string; customer?: string }, command: Command) => {
      const month = options.month === undefined ? undefined : assertMonth(options.month);
      const customerId = options.customer === undefined ? undefined : assertCustomerId(options.customer);
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.listBillableMetricPrices({ month, customer_id: customerId }));
    });
}
