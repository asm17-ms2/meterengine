/**
 * 개발 모드 전용 표 상태 스위치.
 *
 * 디자인 프로토타입의 사이드바 하단 '데모 - 표 상태' 버튼을 옮긴 것이다.
 * 빈 상태, 로딩, 에러를 실제 백엔드 상황을 만들지 않고도 눈으로 확인하려고 둔다.
 *
 * 프로덕션에서는 흔적이 남지 않는다. Next가 process.env.NODE_ENV를 인라인하므로
 * devStateEnabled가 상수 false가 되고 아래 분기가 통째로 제거된다.
 */

export const DEV_STATES = ["normal", "empty", "loading", "error"] as const;
export type DevState = (typeof DEV_STATES)[number];

export const DEV_STATE_LABELS: Record<DevState, string> = {
  normal: "정상",
  empty: "빈 상태",
  loading: "로딩",
  error: "에러",
};

export const devStateEnabled = process.env.NODE_ENV === "development";

/** 개발 모드가 아니면 무엇이 오든 'normal'이다. */
export function readDevState(raw: string | string[] | undefined): DevState {
  if (!devStateEnabled) return "normal";
  const value = Array.isArray(raw) ? raw[0] : raw;
  return DEV_STATES.includes(value as DevState) ? (value as DevState) : "normal";
}
