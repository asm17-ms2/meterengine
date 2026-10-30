export type ReasonHint<Reason extends string> = { reason: Reason; hint: string };

// 확인할 내용은 문제 종류로 정해지는 문장이라, 행마다 반복하지 않고 종류별로 한 번만 보인다.
export function reasonHints<Reason extends string>(issues: { reason: Reason; action_hint: string }[]): ReasonHint<Reason>[] {
  const hints = new Map<Reason, string>();
  for (const issue of issues) if (!hints.has(issue.reason)) hints.set(issue.reason, issue.action_hint);
  return Array.from(hints, ([reason, hint]) => ({ reason, hint }));
}
