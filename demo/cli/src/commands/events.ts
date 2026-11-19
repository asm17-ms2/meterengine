import type { Command } from "commander";
import type { ListEventsParams } from "@meterengine/sdk";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { UsageError } from "../exit-code.ts";
import { assertMonth } from "../month.ts";
import { assertCustomerId } from "../options.ts";


interface ListOptions {
  month?: string;
  from?: string;
  to?: string;
  customer?: string;
  type?: string;
  page?: string;
  size?: string;
}

interface TypesOptions {
  month?: string;
}

function integerOption(name: string, value: string, min: number): number {
  if (!/^\d+$/.test(value) || Number(value) < min) throw new UsageError(`invalid_${name}`, `--${name}는 ${min} 이상의 정수입니다`);
  return Number(value);
}

function listParams(options: ListOptions): ListEventsParams {
  const params: ListEventsParams = {};
  if ((options.from === undefined) !== (options.to === undefined)) throw new UsageError("invalid_period", "--from과 --to는 함께 씁니다");
  if (options.from !== undefined && options.month !== undefined) throw new UsageError("invalid_period", "--month와 --from/--to 중 하나만 씁니다");
  if (options.month !== undefined) params.month = assertMonth(options.month);
  if (options.from !== undefined) params.from = options.from;
  if (options.to !== undefined) params.to = options.to;
  if (options.customer !== undefined) params.customer_id = assertCustomerId(options.customer);
  if (options.type !== undefined) {
    // 빈 type은 서버가 조건 없음으로 받아 전체 목록이 나오므로 보내기 전에 막는다.
    if (options.type.trim() === "") throw new UsageError("invalid_type", "--type에는 이벤트 type을 넣습니다");
    params.type = options.type;
  }
  if (options.page !== undefined) params.page = integerOption("page", options.page, 0);
  if (options.size !== undefined) params.size = integerOption("size", options.size, 1);
  return params;
}

export function registerEvents(program: Command, deps: CliDeps): void {
  const events = program.command("events").description("사용량 이벤트");

  events
    .command("get")
    .description("transaction_id로 이벤트 한 건이 저장됐는지와 저장된 원본을 조회한다 (GET /v1/events/lookup). 없으면 404 event_not_found이고, 서버가 받아 두고 뒤에서 저장하는 설정이면 보낸 직후에는 아직 없을 수 있다")
    .argument("<transaction_id>", "보낼 때 쓴 transaction_id 원문")
    .action(async (transactionId: string, _options: unknown, command: Command) => {
      if (transactionId === "") throw new UsageError("invalid_transaction_id", "transaction_id가 비어 있습니다");
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.getEvent(transactionId));
    });

  events
    .command("list")
    .description("들어온 이벤트를 occurred_at 최신순으로 한 페이지씩 조회한다 (GET /v1/events)")
    .option("--month <YYYY-MM>", "occurred_at 기준 월(KST). 생략하면 이번 달")
    .option("--from <시각>", "--to와 함께 월 대신 기간으로 좁힌다. 날짜나 ISO 8601 시각, 시간대가 없으면 KST. 시작은 포함")
    .option("--to <시각>", "기간의 끝. 시각이면 제외하고, 날짜만 주면 그 날 하루를 포함")
    .option("--customer <uuid>", "고객 UUID로 좁힌다")
    .option("--type <type>", "이벤트 type으로 좁힌다. 어느 과금 미터에도 없는 type도 저장됐으면 나온다")
    .option("--page <n>", "0부터 세는 페이지. 서버 기본 0")
    .option("--size <n>", "한 페이지 개수. 서버 기본 20, 최대 100")
    .action(async (options: ListOptions, command: Command) => {
      const params = listParams(options);
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.listEvents(params));
    });

  events
    .command("types")
    .description("과금 미터 정의와 상관없이 실제로 들어온 이벤트 type 목록을 조회한다 (GET /v1/events/types)")
    .option("--month <YYYY-MM>", "occurred_at 기준 월(KST). 생략하면 이번 달")
    .action(async (options: TypesOptions, command: Command) => {
      const params = options.month === undefined ? {} : { month: assertMonth(options.month) };
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.listEventTypes(params));
    });
}
