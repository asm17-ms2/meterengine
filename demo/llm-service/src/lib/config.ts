import path from "node:path";
import type { AccountChoice } from "./contracts.ts";

export type ServiceAccount = AccountChoice & { customerId: string };
export type LumenConfig = {
  appOrigin: string;
  databasePath: string;
  accounts: ServiceAccount[];
  meterEngineBaseUrl: string;
  meterEngineApiKey: string;
  meterEngineEnvironment?: "test" | "live";
  billingApiKey: string;
  meterEngineConsoleUrl: string;
  flushIntervalMs: number;
  isConfigured: boolean;
};

function httpUrl(value: string): URL {
  const url = new URL(value);
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error("서비스 연결 주소 설정을 확인하세요.");
  }
  return url;
}

function parseAccounts(raw: string | undefined): ServiceAccount[] {
  if (!raw) return [];
  const parsed: unknown = JSON.parse(raw);
  if (!Array.isArray(parsed) || parsed.length > 30) throw new Error("시연 계정 설정을 확인하세요.");
  const accounts: ServiceAccount[] = [];
  for (const candidate of parsed) {
    if (typeof candidate !== "object" || candidate === null) throw new Error("시연 계정 설정을 확인하세요.");
    const account = candidate as Record<string, unknown>;
    if (typeof account.id !== "string" || !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(account.id)
      || typeof account.name !== "string" || !account.name.trim() || account.name.length > 80
      || typeof account.email !== "string" || account.email.length > 200
      || typeof account.customerId !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(account.customerId)) {
      throw new Error("시연 계정 설정을 확인하세요.");
    }
    accounts.push({ id: account.id, name: account.name, email: account.email, customerId: account.customerId.toLowerCase() });
  }
  if (new Set(accounts.map(account => account.id)).size !== accounts.length
    || new Set(accounts.map(account => account.customerId)).size !== accounts.length) {
    throw new Error("시연 계정과 고객 연결은 중복할 수 없습니다.");
  }
  return accounts;
}

export function readConfig(env: Readonly<Record<string, string | undefined>> = process.env): LumenConfig {
  const origin = httpUrl(env.LUMEN_APP_ORIGIN ?? "http://localhost:3200");
  if (origin.pathname !== "/") throw new Error("서비스 주소에는 경로를 넣지 마세요.");
  const accounts = parseAccounts(env.LUMEN_ACCOUNTS);
  const meterEngineApiKey = env.METERENGINE_API_KEY ?? "";
  const meterEngineEnvironment = env.METERENGINE_ENVIRONMENT || undefined;
  if (meterEngineEnvironment !== undefined && meterEngineEnvironment !== "test" && meterEngineEnvironment !== "live") throw new Error("업무 환경은 test 또는 live로 설정하세요.");
  const interval = Number(env.LUMEN_AUTO_FLUSH_INTERVAL_MS ?? "3000");
  if (!Number.isInteger(interval) || interval < 0 || interval > 60000 || (interval > 0 && interval < 500)) {
    throw new Error("사용량 전송 주기를 확인하세요.");
  }
  return {
    appOrigin: origin.origin,
    databasePath: path.resolve(env.LUMEN_DATABASE_PATH ?? path.join(process.cwd(), "data", "lumen.sqlite")),
    accounts,
    meterEngineBaseUrl: httpUrl(env.METERENGINE_API_BASE_URL ?? "http://127.0.0.1:38147").href.replace(/\/$/, ""),
    meterEngineApiKey,
    meterEngineEnvironment,
    billingApiKey: env.METERENGINE_BILLING_API_KEY ?? "",
    meterEngineConsoleUrl: httpUrl(env.METERENGINE_CONSOLE_URL ?? "http://localhost:33147").origin,
    flushIntervalMs: interval,
    isConfigured: accounts.length > 0 && meterEngineApiKey.length > 0,
  };
}

export function viewer(account: ServiceAccount): AccountChoice {
  return { id: account.id, name: account.name, email: account.email };
}
