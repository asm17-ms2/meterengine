import "server-only";

import type { BridgeResult } from "@/lib/bridge-health";
import { requestBridge, type BridgeMethod } from "@/lib/bridge-http";
import { DEFAULT_BRIDGE_URL } from "@/lib/config";

export { bridgeErrorText, isRecord, type BridgeResult } from "@/lib/bridge-health";

const HEALTH_PATH = "/meterengine/health";
const CONFIG_PATH = "/meterengine/config";
const RESTART_PATH = "/meterengine/restart";

export function bridgeUrl(): string {
  const configured = process.env.METERENGINE_BRIDGE_URL;
  return configured === undefined || configured.trim() === "" ? DEFAULT_BRIDGE_URL : configured.trim();
}

async function callBridge(method: BridgeMethod, path: string, body?: unknown): Promise<BridgeResult> {
  try {
    const response = await requestBridge(bridgeUrl(), method, path, body);
    return { ok: true, status: response.status, body: response.body };
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : String(error) };
  }
}

export function getBridgeHealth(): Promise<BridgeResult> {
  return callBridge("GET", HEALTH_PATH);
}

export function getBridgeConfig(): Promise<BridgeResult> {
  return callBridge("GET", CONFIG_PATH);
}

export function updateBridgeConfig(changes: Record<string, unknown>): Promise<BridgeResult> {
  return callBridge("PUT", CONFIG_PATH, changes);
}

export function restartBridge(): Promise<BridgeResult> {
  return callBridge("POST", RESTART_PATH);
}
