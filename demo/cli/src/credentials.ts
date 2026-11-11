import { chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { UsageError } from "./exit-code.ts";

export const API_KEY_ENV = "METERENGINE_API_KEY";
const CREDENTIALS_FILE = "credentials.json";
const API_KEY_PATTERN = /^me_(live|test)_[A-Za-z0-9]{32}$/;

export function credentialsPath(configDir: string): string {
  return join(configDir, CREDENTIALS_FILE);
}

export function assertApiKeyFormat(apiKey: string): void {
  if (!API_KEY_PATTERN.test(apiKey)) {
    throw new UsageError("invalid_api_key_format", "API 키 형식이 아닙니다. me_test_ 또는 me_live_ 뒤에 영문과 숫자 32자입니다");
  }
}

// 다른 사용자가 읽지 못하게 디렉터리는 700, 파일은 600으로 둔다. 이미 있던 파일도 600으로 되돌린다.
export async function saveApiKey(configDir: string, apiKey: string): Promise<string> {
  assertApiKeyFormat(apiKey);
  await mkdir(configDir, { recursive: true, mode: 0o700 });
  const path = credentialsPath(configDir);
  await writeFile(path, `${JSON.stringify({ api_key: apiKey }, null, 2)}\n`, { mode: 0o600 });
  await chmod(path, 0o600);
  return path;
}

// 환경변수가 있으면 환경변수, 없으면 login으로 저장한 파일을 쓴다.
export async function resolveApiKey(env: NodeJS.ProcessEnv, configDir: string): Promise<string> {
  const fromEnv = env[API_KEY_ENV]?.trim();
  if (fromEnv) {
    assertApiKeyFormat(fromEnv);
    return fromEnv;
  }
  let text: string;
  try {
    text = await readFile(credentialsPath(configDir), "utf8");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      throw new UsageError("missing_api_key", `API 키가 없습니다. meterengine login으로 저장하거나 ${API_KEY_ENV}를 설정하세요`);
    }
    throw error;
  }
  const saved: unknown = JSON.parse(text);
  const apiKey = typeof saved === "object" && saved !== null && "api_key" in saved ? saved.api_key : undefined;
  if (typeof apiKey !== "string") {
    throw new UsageError("invalid_credentials_file", `${credentialsPath(configDir)}에 api_key가 없습니다. meterengine login을 다시 하세요`);
  }
  assertApiKeyFormat(apiKey);
  return apiKey;
}
