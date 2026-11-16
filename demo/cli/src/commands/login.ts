import type { Command } from "commander";
import { API_KEY_ENV, DEFAULT_PROFILE, keyMode, saveApiKey } from "../credentials.ts";
import type { CliDeps } from "../context.ts";
import { printJson, profileOf } from "../context.ts";

export function registerLogin(program: Command, deps: CliDeps): void {
  program
    .command("login")
    .description(`API 키를 표준 입력으로 받아 저장한다. --profile로 이름을 붙여 키를 여러 개 둘 수 있다. ${API_KEY_ENV}가 있으면 --profile 없는 명령은 그 키를 먼저 쓴다`)
    .action(async (_options: unknown, command: Command) => {
      const profile = profileOf(command) ?? DEFAULT_PROFILE;
      const apiKey = (await deps.readSecret()).trim();
      const path = await saveApiKey(deps.configDir, apiKey, profile);
      printJson(deps.stdout, { saved_to: path, profile, mode: keyMode(apiKey) });
    });
}
