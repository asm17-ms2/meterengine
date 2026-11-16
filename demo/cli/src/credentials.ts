import { randomBytes } from "node:crypto";
import { chmod, mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { UsageError } from "./exit-code.ts";

export const API_KEY_ENV = "METERENGINE_API_KEY";
export const DEFAULT_PROFILE = "default";
const CREDENTIALS_FILE = "credentials.json";
const API_KEY_PATTERN = /^me_(live|test)_[A-Za-z0-9]{32}$/;
const PROFILE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/;

type Profiles = Record<string, { api_key: string }>;

export function credentialsPath(configDir: string): string {
  return join(configDir, CREDENTIALS_FILE);
}

export function assertApiKeyFormat(apiKey: string): void {
  if (!API_KEY_PATTERN.test(apiKey)) {
    throw new UsageError("invalid_api_key_format", "API 키 형식이 아닙니다. me_test_ 또는 me_live_ 뒤에 영문과 숫자 32자입니다");
  }
}

export function assertProfileName(profile: string): void {
  if (!PROFILE_PATTERN.test(profile)) {
    throw new UsageError("invalid_profile_name", "프로필 이름은 영문, 숫자, -, _로 32자 이내이고 영문이나 숫자로 시작합니다");
  }
}

export function keyMode(apiKey: string): "test" | "live" {
  return apiKey.startsWith("me_live_") ? "live" : "test";
}

// 프로필 없는 {api_key} 형식 파일은 default 프로필 하나로 읽는다.
async function readProfiles(configDir: string): Promise<Profiles | undefined> {
  let text: string;
  try {
    text = await readFile(credentialsPath(configDir), "utf8");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return undefined;
    throw error;
  }
  let saved: unknown;
  try {
    saved = JSON.parse(text);
  } catch {
    throw new UsageError("invalid_credentials_file", `${credentialsPath(configDir)}를 JSON으로 읽을 수 없습니다. meterengine login을 다시 하세요`);
  }
  if (typeof saved !== "object" || saved === null) return {};
  if ("profiles" in saved && typeof saved.profiles === "object" && saved.profiles !== null) {
    const profiles: Profiles = {};
    for (const [name, value] of Object.entries(saved.profiles)) {
      if (typeof value === "object" && value !== null && "api_key" in value && typeof value.api_key === "string") profiles[name] = { api_key: value.api_key };
    }
    return profiles;
  }
  if ("api_key" in saved && typeof saved.api_key === "string") return { [DEFAULT_PROFILE]: { api_key: saved.api_key } };
  return {};
}

// 다른 사용자가 읽지 못하게 디렉터리는 700, 파일은 600으로 둔다. 600으로 만든 임시 파일을 바꿔 넣어 넓은 권한으로 키가 담기는 순간이 없다.
export async function saveApiKey(configDir: string, apiKey: string, profile = DEFAULT_PROFILE): Promise<string> {
  assertApiKeyFormat(apiKey);
  assertProfileName(profile);
  let profiles: Profiles;
  try {
    profiles = (await readProfiles(configDir)) ?? {};
  } catch (error) {
    if (!(error instanceof UsageError)) throw error;
    profiles = {};
  }
  profiles[profile] = { api_key: apiKey };
  await mkdir(configDir, { recursive: true, mode: 0o700 });
  await chmod(configDir, 0o700);
  const path = credentialsPath(configDir);
  const temporary = `${path}.${randomBytes(6).toString("hex")}.tmp`;
  try {
    await writeFile(temporary, `${JSON.stringify({ profiles }, null, 2)}\n`, { mode: 0o600, flag: "wx" });
    await rename(temporary, path);
  } catch (error) {
    await rm(temporary, { force: true });
    throw error;
  }
  return path;
}

// --profile을 주면 그 프로필, 아니면 환경변수, 그것도 없으면 default 프로필을 쓴다.
export async function resolveApiKey(env: NodeJS.ProcessEnv, configDir: string, profile?: string): Promise<string> {
  if (profile === undefined) {
    const fromEnv = env[API_KEY_ENV]?.trim();
    if (fromEnv) {
      assertApiKeyFormat(fromEnv);
      return fromEnv;
    }
  } else {
    assertProfileName(profile);
  }
  const name = profile ?? DEFAULT_PROFILE;
  const profiles = await readProfiles(configDir);
  const apiKey = profiles?.[name]?.api_key;
  if (apiKey === undefined) {
    if (profile === undefined) {
      throw new UsageError("missing_api_key", `API 키가 없습니다. meterengine login으로 저장하거나 ${API_KEY_ENV}를 설정하세요`);
    }
    const saved = Object.keys(profiles ?? {});
    const hint = saved.length === 0 ? "저장한 프로필이 없습니다" : `저장한 프로필: ${saved.join(", ")}`;
    throw new UsageError("missing_profile", `${name} 프로필이 없습니다. meterengine login --profile ${name}으로 저장하세요. ${hint}`);
  }
  assertApiKeyFormat(apiKey);
  return apiKey;
}
