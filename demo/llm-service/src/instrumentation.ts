export async function register(): Promise<void> {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    const { startMeteringWorker } = await import("./lib/runtime.ts");
    startMeteringWorker();
  }
}
