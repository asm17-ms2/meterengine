import { createHash } from "node:crypto";
import { parseTargets, type Target, type TargetEnv } from "./config.ts";

export type WebhookScope = { id: string; environment: "legacy" | "test" | "live"; isLegacy: boolean };
export type WebhookTarget = { target: Target; scope: WebhookScope; secret: string | undefined };
export type WebhookTargetResult = { ok: true; value: WebhookTarget } | { ok: false; reason: "target_required" | "unknown_target" | "invalid_target_config" };

function hash(value: string): string { return createHash("sha256").update(value).digest("hex"); }

export function webhookTarget(target: Target, env: TargetEnv): WebhookTarget {
  const isLegacy = target.environment === undefined && env.METERENGINE_LEGACY_WEBHOOK_TARGET === target.name;
  const environment = target.environment ?? "legacy";
  const id = hash(JSON.stringify([1, target.name, target.base_url.replace(/\/+$/, ""), environment, hash(target.api_key)]));
  return { target, scope: { id, environment, isLegacy }, secret: target.webhook_secret ?? (isLegacy ? env.METERENGINE_WEBHOOK_SECRET : undefined) };
}

export function selectWebhookTarget(values: string[], env: TargetEnv): WebhookTargetResult {
  if (values.length !== 1 || !values[0]) return { ok: false, reason: "target_required" };
  let targets: Target[];
  try { targets = parseTargets(env); }
  catch { return { ok: false, reason: "invalid_target_config" }; }
  const target = targets.find(candidate => candidate.name === values[0]);
  if (!target) return { ok: false, reason: "unknown_target" };
  if (env.METERENGINE_LEGACY_WEBHOOK_TARGET === target.name && target.environment !== undefined) return { ok: false, reason: "invalid_target_config" };
  return { ok: true, value: webhookTarget(target, env) };
}
