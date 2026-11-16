import type { Command } from "commander";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { UsageError } from "../exit-code.ts";
import { assertMonth } from "../month.ts";
import { UUID_PATTERN, assertCustomerId } from "../options.ts";

export function registerInvoices(program: Command, deps: CliDeps): void {
  const invoices = program.command("invoices").description("인보이스");
  invoices
    .command("draft")
    .description("청구 예정액을 조회한다 (GET /v1/invoices/draft)")
    .option("--month <YYYY-MM>", "청구 월. 생략하면 이번 달(KST)")
    .action(async (options: { month?: string }, command: Command) => {
      const month = options.month === undefined ? undefined : assertMonth(options.month);
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.previewDraftInvoice(month === undefined ? {} : { month }));
    });

  invoices
    .command("list")
    .description("확정된 인보이스 목록을 조회한다 (GET /v1/invoices)")
    .option("--month <YYYY-MM>", "인보이스의 달(KST). 생략하면 모든 달")
    .option("--customer <uuid>", "고객 UUID로 좁힌다")
    .action(async (options: { month?: string; customer?: string }, command: Command) => {
      const month = options.month === undefined ? undefined : assertMonth(options.month);
      const customerId = options.customer === undefined ? undefined : assertCustomerId(options.customer);
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.listInvoices({ month, customer_id: customerId }));
    });

  invoices
    .command("get")
    .description("확정된 인보이스 하나를 조회한다 (GET /v1/invoices/{id})")
    .argument("<id>", "인보이스 UUID")
    .action(async (id: string, _options: unknown, command: Command) => {
      if (!UUID_PATTERN.test(id)) throw new UsageError("invalid_invoice_id", "인보이스 id는 UUID입니다. invoices list로 찾을 수 있습니다");
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.getInvoice(id));
    });
}
