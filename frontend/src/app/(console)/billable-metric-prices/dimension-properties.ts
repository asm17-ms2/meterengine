export const DIMENSION_PROPERTIES_FIELD = "dimension_properties";

export const BLANK_KEY_MESSAGE = "빈 키를 담을 수 없습니다";
export const DUPLICATE_KEY_MESSAGE = "같은 키가 두 번 있습니다";
const MALFORMED_KEY_MESSAGE = "키 형식이 잘못됐습니다";

export type DimensionPropertiesReadResult =
  | { ok: true; keys: string[] }
  | { ok: false; message: string };

export function rejectDimensionProperty(key: string, existing: string[]): string | undefined {
  if (key.trim() === "") return BLANK_KEY_MESSAGE;
  if (existing.includes(key)) return DUPLICATE_KEY_MESSAGE;
  return undefined;
}

export function readDimensionProperties(formData: FormData): DimensionPropertiesReadResult {
  const raws = formData.getAll(DIMENSION_PROPERTIES_FIELD);
  if (raws.some((raw) => typeof raw !== "string")) {
    return { ok: false, message: MALFORMED_KEY_MESSAGE };
  }
  const keys = raws as string[];
  for (const [index, key] of keys.entries()) {
    const rejection = rejectDimensionProperty(key, keys.slice(0, index));
    if (rejection) return { ok: false, message: rejection };
  }
  return { ok: true, keys };
}
