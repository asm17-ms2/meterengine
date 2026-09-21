export const DEV_STATES = ["normal", "empty", "loading", "error"] as const;
export type DevState = (typeof DEV_STATES)[number];

export const DEV_STATE_LABELS: Record<DevState, string> = {
  normal: "정상",
  empty: "빈 상태",
  loading: "로딩",
  error: "에러",
};

export const isDevStateEnabled = process.env.NODE_ENV === "development";

export function readDevState(raw: string | string[] | undefined): DevState {
  if (!isDevStateEnabled) return "normal";
  const value = Array.isArray(raw) ? raw[0] : raw;
  return DEV_STATES.includes(value as DevState) ? (value as DevState) : "normal";
}
