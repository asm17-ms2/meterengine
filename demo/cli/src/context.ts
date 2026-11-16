import type { Command } from "commander";
import { MeterEngineClient } from "@meterengine/sdk";
import type { MeterEngineClientOptions } from "@meterengine/sdk";
import { resolveApiKey } from "./credentials.ts";
import { UsageError } from "./exit-code.ts";

export const BASE_URL_ENV = "METERENGINE_API_BASE_URL";
// 가이드의 공개 API 주소다. 가이드에 "확인 필요"로 남아 있어 주소가 확정되면 다시 본다.
const DEFAULT_BASE_URL = "https://meterengine.com";

export interface CliDeps {
  env: NodeJS.ProcessEnv;
  configDir: string;
  stdout: (text: string) => void;
  stderr: (text: string) => void;
  readSecret: () => Promise<string>;
  readFile: (path: string) => Promise<string>;
  now?: () => Date;
  clientOptions?: Partial<MeterEngineClientOptions>;
}

export function printJson(write: (text: string) => void, value: unknown): void {
  write(`${JSON.stringify(value, null, 2)}\n`);
}

// 전역 옵션 --profile은 하위 명령 뒤에 써도 받는다.
export function profileOf(command: Command): string | undefined {
  const profile: unknown = command.optsWithGlobals().profile;
  return typeof profile === "string" ? profile : undefined;
}

// 다시 해도 소용없는 주소 설정 오류를 연결 실패(종료 코드 3)로 내지 않게 보내기 전에 거른다.
export function baseUrlOf(env: NodeJS.ProcessEnv): string {
  const baseUrl = env[BASE_URL_ENV]?.trim() || DEFAULT_BASE_URL;
  const url = URL.parse(baseUrl);
  if (url === null || (url.protocol !== "http:" && url.protocol !== "https:") || url.username !== "" || url.password !== "" || url.search !== "" || url.hash !== "") {
    throw new UsageError("invalid_base_url", `${BASE_URL_ENV}는 http:// 또는 https://로 시작하는 주소이고 계정, ?, #을 넣지 않습니다. 예: http://localhost:8080`);
  }
  return baseUrl;
}

export async function createClient(deps: CliDeps, profile?: string): Promise<MeterEngineClient> {
  const baseUrl = baseUrlOf(deps.env);
  const apiKey = await resolveApiKey(deps.env, deps.configDir, profile);
  return new MeterEngineClient({ ...deps.clientOptions, baseUrl, apiKey });
}

export function clientFor(deps: CliDeps, command: Command): Promise<MeterEngineClient> {
  return createClient(deps, profileOf(command));
}
