export type ParsedProperties = { properties: Record<string, unknown> } | { message: string };

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

export function formatKstTimestamp(date: Date): string {
  return `${new Date(date.getTime() + KST_OFFSET_MS).toISOString().slice(0, 19)}+09:00`;
}

export function parseEventProperties(text: string): ParsedProperties {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { message: "properties가 JSON이 아닙니다" };
  }
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    return { message: "properties는 JSON 객체여야 합니다" };
  }
  return { properties: parsed as Record<string, unknown> };
}
