import { Command } from "commander";
import { MeterEngineClient } from "@meterengine/sdk";
import type { MeterEngineClientOptions } from "@meterengine/sdk";
import { API_KEY_ENV, resolveApiKey, saveApiKey } from "./credentials.ts";
import { EXIT_OK, errorOutputFor, exitCodeFor } from "./exit-code.ts";

export const BASE_URL_ENV = "METERENGINE_API_BASE_URL";
// 가이드의 공개 API 주소다. 가이드에 "확인 필요"로 남아 있어 주소가 확정되면 다시 본다.
const DEFAULT_BASE_URL = "https://meterengine.com";

export interface CliDeps {
  env: NodeJS.ProcessEnv;
  configDir: string;
  stdout: (text: string) => void;
  stderr: (text: string) => void;
  readSecret: () => Promise<string>;
  clientOptions?: Partial<MeterEngineClientOptions>;
}

function printJson(write: (text: string) => void, value: unknown): void {
  write(`${JSON.stringify(value, null, 2)}\n`);
}

async function createClient(deps: CliDeps): Promise<MeterEngineClient> {
  const apiKey = await resolveApiKey(deps.env, deps.configDir);
  const baseUrl = deps.env[BASE_URL_ENV]?.trim() || DEFAULT_BASE_URL;
  return new MeterEngineClient({ ...deps.clientOptions, baseUrl, apiKey });
}

function buildProgram(deps: CliDeps): Command {
  const program = new Command("meterengine")
    .description("MeterEngine API를 터미널에서 부른다. 결과는 JSON으로, 오류는 표준 오류에 JSON으로 낸다")
    .exitOverride()
    .configureOutput({ writeOut: deps.stdout, writeErr: deps.stderr, outputError: () => {} });

  program
    .command("login")
    .description(`API 키를 표준 입력으로 받아 저장한다. ${API_KEY_ENV}가 있으면 저장한 키보다 먼저 쓴다`)
    .action(async () => {
      const apiKey = (await deps.readSecret()).trim();
      const path = await saveApiKey(deps.configDir, apiKey);
      printJson(deps.stdout, { saved_to: path, mode: apiKey.startsWith("me_live_") ? "live" : "test" });
    });

  const invoices = program.command("invoices").description("인보이스");
  invoices
    .command("draft")
    .description("청구 예정액을 조회한다 (GET /v1/invoices/draft)")
    .option("--month <YYYY-MM>", "청구 월. 생략하면 이번 달(KST)")
    .action(async (options: { month?: string }) => {
      const client = await createClient(deps);
      printJson(deps.stdout, await client.previewDraftInvoice(options.month === undefined ? {} : { month: options.month }));
    });

  return program;
}

// 실행하고 종료 코드를 돌려준다. 실패는 표준 오류에 {code, message} JSON으로 낸다.
export async function run(argv: string[], deps: CliDeps): Promise<number> {
  try {
    await buildProgram(deps).parseAsync(argv, { from: "user" });
    return EXIT_OK;
  } catch (error) {
    const code = exitCodeFor(error);
    if (code !== EXIT_OK) printJson(deps.stderr, errorOutputFor(error));
    return code;
  }
}
