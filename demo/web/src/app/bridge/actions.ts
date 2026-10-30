"use server";

import { bridgeErrorText, getBridgeConfig, isRecord, restartBridge, updateBridgeConfig } from "@/lib/bridge-client";
import { toBridgeChanges, toBridgeFields } from "@/lib/bridge-form";
import { assertLocalRequest } from "@/lib/local-request";

export type BridgeSaveState =
  | { status: "idle" }
  | { status: "invalid"; field: string; message: string }
  | { status: "saved"; restart_required: boolean }
  | { status: "rejected"; http_status: number; error: string; field: string | null }
  | { status: "failed"; message: string };

export type BridgeRestartState =
  | { status: "idle" }
  | { status: "restarting" }
  | { status: "not_launchd" }
  | { status: "rejected"; http_status: number; error: string }
  | { status: "failed"; message: string };

function readText(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

function errorOf(body: unknown, fallback: string): string {
  return isRecord(body) && typeof body.error === "string" ? body.error : fallback;
}

export async function updateBridgeConfigAction(_previous: BridgeSaveState, formData: FormData): Promise<BridgeSaveState> {
  await assertLocalRequest();
  const current = await getBridgeConfig();
  if (!current.ok || current.status !== 200 || !isRecord(current.body)) {
    return { status: "failed", message: `저장 전에 지금 설정을 읽지 못했습니다: ${bridgeErrorText(current)}` };
  }
  const converted = toBridgeChanges(toBridgeFields(current.body), (name) => readText(formData, name));
  if ("field" in converted) {
    return { status: "invalid", field: converted.field, message: converted.message };
  }
  const result = await updateBridgeConfig(converted.changes);
  if (!result.ok) {
    return { status: "failed", message: result.message };
  }
  if (result.status === 200) {
    const isRestartRequired = !isRecord(result.body) || result.body.restart_required !== false;
    return { status: "saved", restart_required: isRestartRequired };
  }
  const field = isRecord(result.body) && typeof result.body.field === "string" ? result.body.field : null;
  return { status: "rejected", http_status: result.status, error: errorOf(result.body, "저장하지 못했습니다"), field };
}

export async function restartBridgeAction(): Promise<BridgeRestartState> {
  await assertLocalRequest();
  const result = await restartBridge();
  if (!result.ok) {
    return { status: "failed", message: result.message };
  }
  if (result.status === 202) {
    return { status: "restarting" };
  }
  if (result.status === 409) {
    return { status: "not_launchd" };
  }
  return { status: "rejected", http_status: result.status, error: errorOf(result.body, "재시작하지 못했습니다") };
}
