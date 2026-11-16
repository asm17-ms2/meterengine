const inFlight = new Map<string, Promise<unknown>>();

// 같은 키의 요청이 진행 중이면 새로 보내지 않고 그 결과를 같이 받는다. 끝나면 지워 결과를 남기지 않는다.
export function shareInFlight<T>(key: string, run: () => Promise<T>): Promise<T> {
  const running = inFlight.get(key);
  if (running) return running as Promise<T>;
  const started = run().finally(() => inFlight.delete(key));
  inFlight.set(key, started);
  return started;
}
