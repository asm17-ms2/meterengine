import "server-only";

import { readConfig } from "./config.ts";
import { LumenService } from "./service.ts";
import { ServiceError } from "./service-error.ts";
import { LumenStore } from "./store.ts";

type RuntimeState = { lumenRuntime?: LumenService; lumenFlushTimer?: ReturnType<typeof setInterval> };
const runtimeState = globalThis as typeof globalThis & RuntimeState;

export function getRuntime(): LumenService {
  if (!runtimeState.lumenRuntime) {
    const config = readConfig();
    if (!config.isConfigured) throw new ServiceError(503, "service_not_configured", "서비스 연결 설정이 필요합니다. 운영자에게 문의하세요.");
    runtimeState.lumenRuntime = new LumenService({ config, store: new LumenStore(config.databasePath, config.accounts) });
  }
  return runtimeState.lumenRuntime;
}

export function startMeteringWorker(): void {
  if (runtimeState.lumenFlushTimer) return;
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
    runtimeState.lumenFlushTimer = setInterval(flush, config.flushIntervalMs);
    runtimeState.lumenFlushTimer.unref();
    flush();
  } catch {
    // 잘못된 설정은 API에서 알리고 키나 연결 문자열을 로그에 남기지 않는다.
  }
}
