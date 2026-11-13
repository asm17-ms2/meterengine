import "server-only";

import { readConfig } from "./config.ts";
import { OpenAbService } from "./service.ts";
import { ServiceError } from "./service-error.ts";
import { OpenAbStore } from "./store.ts";

type RuntimeState = { openAbRuntime?: OpenAbService; openAbFlushTimer?: ReturnType<typeof setInterval> };
const runtimeState = globalThis as typeof globalThis & RuntimeState;

export function getRuntime(): OpenAbService {
  if (!runtimeState.openAbRuntime) {
    const config = readConfig();
    if (!config.isConfigured) throw new ServiceError(503, "service_not_configured", "서비스 연결 설정이 필요해요. 서비스 운영자에게 문의해 주세요.");
    const store = new OpenAbStore(config.databasePath, config.accounts);
    try { runtimeState.openAbRuntime = new OpenAbService({ config, store }); }
    catch (error) { store.close(); throw error; }
  }
  return runtimeState.openAbRuntime;
}

export function startMeteringWorker(): void {
  if (runtimeState.openAbFlushTimer) return;
  try {
    const config = readConfig();
    if (!config.isConfigured || config.flushIntervalMs === 0) return;
    const service = getRuntime();
    let isFlushing = false;
    const flush = () => {
      if (isFlushing) return;
      isFlushing = true;
      void service.flushMetering().catch(() => undefined).finally(() => { isFlushing = false; });
    };
    runtimeState.openAbFlushTimer = setInterval(flush, config.flushIntervalMs);
    runtimeState.openAbFlushTimer.unref();
    flush();
  } catch {
    // 잘못된 설정은 API에서 알리고 키나 연결 문자열을 로그에 남기지 않는다.
  }
}
