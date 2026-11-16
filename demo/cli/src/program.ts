import { Command, CommanderError } from "commander";
import { registerApi } from "./commands/api.ts";
import { registerCustomers } from "./commands/customers.ts";
import { registerDiagnose } from "./commands/diagnose.ts";
import { registerDiff } from "./commands/diff.ts";
import { registerEvents } from "./commands/events.ts";
import { registerInvoices } from "./commands/invoices.ts";
import { registerLogin } from "./commands/login.ts";
import { registerMeters } from "./commands/meters.ts";
import { registerPrices } from "./commands/prices.ts";
import { registerUsage } from "./commands/usage.ts";
import { registerVerify } from "./commands/verify.ts";
import type { CliDeps } from "./context.ts";
import { printJson } from "./context.ts";
import { EXIT_OK, UsageError, errorOutputFor, exitCodeFor } from "./exit-code.ts";

export { BASE_URL_ENV } from "./context.ts";
export type { CliDeps } from "./context.ts";

// 하위 명령을 빼먹으면 commander가 도움말을 표준 오류에 쓴다. 그 도움말은 버리고 하위 명령 목록을 담은 오류 JSON으로 바꾼다.
function missingSubcommand(command: Command): UsageError {
  const path: string[] = [];
  for (let current: Command | null = command; current !== null; current = current.parent) path.unshift(current.name());
  const names = command.commands.map((sub) => sub.name()).join(", ");
  return new UsageError("invalid_usage", `${path.join(" ")} 뒤에 하위 명령을 쓰세요: ${names}. 자세한 것은 ${path.join(" ")} --help`);
}

function buildProgram(deps: CliDeps, onErrorHelp: (command: Command) => void): Command {
  const program = new Command("meterengine")
    .description("MeterEngine API를 터미널에서 조회하고 진단한다. 결과는 JSON으로, 오류는 표준 오류에 JSON으로 낸다")
    .option("--profile <name>", "login --profile로 저장한 키를 쓴다. 생략하면 METERENGINE_API_KEY, 그다음 default 프로필")
    .exitOverride()
    .configureHelp({ showGlobalOptions: true })
    .addHelpText(
      "after",
      "\n종료 코드: 0 성공, 1 서버가 거절(입력을 고친다), 2 사용법 오류(서버에 보내기 전), 3 다시 하면 될 수 있음(연결, 시간 초과, 429, 5xx).\n" +
        "diagnose, verify, diff는 끝까지 하면 판정과 관계없이 0이고 판정은 JSON 안에 있다.",
    )
    .configureOutput({ writeOut: deps.stdout, writeErr: () => {}, outputError: () => {} });
  program.on("beforeAllHelp", (context: { error: boolean; command: Command }) => {
    if (context.error) onErrorHelp(context.command);
  });

  registerLogin(program, deps);
  registerUsage(program, deps);
  registerEvents(program, deps);
  registerCustomers(program, deps);
  registerMeters(program, deps);
  registerPrices(program, deps);
  registerInvoices(program, deps);
  registerDiagnose(program, deps);
  registerVerify(program, deps);
  registerDiff(program, deps);
  registerApi(program, deps);
  return program;
}

// 실행하고 종료 코드를 돌려준다. 실패는 표준 오류에 {code, message} JSON으로 낸다.
export async function run(argv: string[], deps: CliDeps): Promise<number> {
  let helpedCommand: Command | undefined;
  try {
    await buildProgram(deps, (command) => (helpedCommand = command)).parseAsync(argv, { from: "user" });
    return EXIT_OK;
  } catch (thrown) {
    const isMissingSubcommand = thrown instanceof CommanderError && thrown.code === "commander.help" && thrown.exitCode !== 0;
    const error = isMissingSubcommand && helpedCommand !== undefined ? missingSubcommand(helpedCommand) : thrown;
    const code = exitCodeFor(error);
    if (code !== EXIT_OK) printJson(deps.stderr, errorOutputFor(error));
    return code;
  }
}
