import type { Command } from "commander";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { UsageError } from "../exit-code.ts";
import { assertMonth, kstMonthOf, previousMonth } from "../month.ts";
import { diagnoseEvent } from "./diagnose-event.ts";
import { diagnoseMonth } from "./diagnose-month.ts";

// 서버의 lookup이 받는 transaction_id 길이 상한이다.
const TRANSACTION_ID_LIMIT = 1024;

// 여러 번 받은 --month를 모은다. 기본값을 두지 않아 도움말에 빈 배열이 보이지 않는다.
function collectMonth(value: string, previous: string[] | undefined): string[] {
  return [...(previous ?? []), value];
}

function rejectionMonths(requested: string[] | undefined, now: Date): string[] {
  const months = requested === undefined ? [kstMonthOf(now), previousMonth(kstMonthOf(now))] : requested.map(assertMonth);
  return [...new Set(months)].sort().reverse();
}

export function registerDiagnose(program: Command, deps: CliDeps): void {
  const diagnose = program.command("diagnose").description("여러 조회를 묶어 청구에서 빠지는 이유를 진단한다. 진단을 끝까지 하면 판정과 관계없이 종료 코드 0이고 판정은 JSON에 있다");

  diagnose
    .command("event")
    .description(
      "이벤트 한 건이 저장됐는지, 청구에 드는지, 아니면 왜 빠지는지와 조치를 진단한다 (GET /v1/events/lookup, /v1/invoices, /v1/event-issues/search, /v1/usage, /v1/invoices/draft, /v1/event-rejections/search). 저장됐으면 occurred_at의 KST 달로 보고, 저장 안 됐으면 거절 기록을 찾는다",
    )
    .argument("<transaction_id>", "보낼 때 쓴 transaction_id 원문")
    .option("--month <YYYY-MM>", "저장 안 된 이벤트의 거절 기록을 찾을 달(보낸 달, KST). 여러 번 쓸 수 있다. 생략하면 이번 달과 지난달", collectMonth)
    .action(async (transactionId: string, options: { month?: string[] }, command: Command) => {
      if (transactionId === "") throw new UsageError("invalid_transaction_id", "transaction_id가 비어 있습니다");
      if (transactionId.length > TRANSACTION_ID_LIMIT) throw new UsageError("invalid_transaction_id", `transaction_id는 ${TRANSACTION_ID_LIMIT}자 이하입니다`);
      const now = deps.now?.() ?? new Date();
      const months = rejectionMonths(options.month, now);
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await diagnoseEvent(client, transactionId, months, now));
    });

  diagnose
    .command("month")
    .description(
      "그 달 청구에서 빠지거나 빠질 이벤트와 금액 조합, 거절 수, 과금 미터 없는 이벤트 유형, 자동 확정 일정을 한 번에 진단한다 (GET /v1/unbilled-events, /v1/event-issues/search, /v1/event-rejections/search, /v1/events/types, /v1/usage, /v1/events, /v1/invoices, /v1/invoices/draft)",
    )
    .option("--month <YYYY-MM>", "청구 월(KST). 생략하면 이번 달. 거절 수는 이 달에 거절한 것을 센다")
    .action(async (options: { month?: string }, command: Command) => {
      const now = deps.now?.() ?? new Date();
      const month = options.month === undefined ? kstMonthOf(now) : assertMonth(options.month);
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await diagnoseMonth(client, month, now));
    });
}
