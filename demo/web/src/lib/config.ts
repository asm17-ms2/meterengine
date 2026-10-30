export type Target = { name: string; base_url: string; api_key: string; environment?: "test" | "live"; webhook_secret?: string };

export type TargetEnv = Readonly<Record<string, string | undefined>>;

export const DEFAULT_BRIDGE_URL = "http://127.0.0.1:4318";

const LOCAL_TARGET_NAME = "local";
const LOCAL_BASE_URL = "http://localhost:8080";

function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function toTarget(value: unknown, position: number): Target {
  const label = `METERENGINE_TARGETS의 ${position + 1}번째 대상`;
  if (typeof value !== "object" || value === null) {
    throw new Error(`${label}이 객체가 아닙니다`);
  }
  const { name, base_url: baseUrl, api_key: apiKey, environment, webhook_secret: webhookSecret } = value as Record<string, unknown>;
  if (typeof name !== "string" || name.trim() === "") {
    throw new Error(`${label}의 name이 비었습니다`);
  }
  if (typeof baseUrl !== "string" || !isHttpUrl(baseUrl)) {
    throw new Error(`${label}(${name})의 base_url이 http(s) 주소가 아닙니다`);
  }
  if (typeof apiKey !== "string" || apiKey === "") {
    throw new Error(`${label}(${name})의 api_key가 비었습니다`);
  }
  if (environment !== undefined && environment !== "test" && environment !== "live") throw new Error(`${label}(${name})의 environment는 test 또는 live여야 합니다`);
  if (webhookSecret !== undefined && typeof webhookSecret !== "string") throw new Error(`${label}(${name})의 webhook_secret은 문자열이어야 합니다`);
  return { name, base_url: baseUrl, api_key: apiKey, ...(environment ? { environment } : {}), ...(webhookSecret !== undefined ? { webhook_secret: webhookSecret } : {}) };
}

export function parseTargets(env: TargetEnv): Target[] {
  const raw = env.METERENGINE_TARGETS;
  if (raw === undefined || raw.trim() === "") {
    const environment = env.METERENGINE_ENVIRONMENT || undefined;
    if (environment !== undefined && environment !== "test" && environment !== "live") throw new Error("METERENGINE_ENVIRONMENT는 test 또는 live여야 합니다");
    const webhookSecret = environment ? env.METERENGINE_WEBHOOK_SECRET || undefined : undefined;
    return [{ name: LOCAL_TARGET_NAME, base_url: LOCAL_BASE_URL, api_key: env.METERENGINE_API_KEY ?? "", ...(environment ? { environment } : {}), ...(webhookSecret ? { webhook_secret: webhookSecret } : {}) }];
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error("METERENGINE_TARGETS가 JSON이 아닙니다");
  }
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error("METERENGINE_TARGETS는 대상이 하나 이상인 JSON 배열이어야 합니다");
  }
  const targets = parsed.map(toTarget);
  const names = new Set<string>();
  for (const target of targets) {
    if (names.has(target.name)) {
      throw new Error(`METERENGINE_TARGETS에 같은 이름이 있습니다: ${target.name}`);
    }
    names.add(target.name);
  }
  return targets;
}
