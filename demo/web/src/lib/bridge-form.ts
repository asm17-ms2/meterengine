export type BridgeFieldKind = "text" | "list" | "number" | "secret";

export type BridgeField = { name: string; kind: BridgeFieldKind; value: string; placeholder?: string };

export type BridgeChanges = { changes: Record<string, unknown> } | { field: string; message: string };

const SECRET_FIELD_NAMES = new Set(["api_key"]);

function toField(name: string, value: unknown): BridgeField | undefined {
  if (SECRET_FIELD_NAMES.has(name)) {
    const field: BridgeField = { name, kind: "secret", value: "" };
    if (typeof value === "string" && value !== "") {
      field.placeholder = value;
    }
    return field;
  }
  if (typeof value === "string") {
    return { name, kind: "text", value };
  }
  if (typeof value === "number") {
    return { name, kind: "number", value: String(value) };
  }
  if (Array.isArray(value) && value.every((item) => typeof item === "string")) {
    return { name, kind: "list", value: value.join(", ") };
  }
  return undefined;
}

export function toBridgeFields(config: Record<string, unknown>): BridgeField[] {
  return Object.entries(config)
    .map(([name, value]) => toField(name, value))
    .filter((field): field is BridgeField => field !== undefined);
}

function splitList(text: string): string[] {
  return text
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item !== "");
}

const NUMBER_PATTERN = /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/;

export function toBridgeChanges(fields: BridgeField[], read: (name: string) => string): BridgeChanges {
  const changes: Record<string, unknown> = {};
  for (const field of fields) {
    const text = read(field.name);
    if (field.kind === "list") {
      changes[field.name] = splitList(text);
    } else if (field.kind === "number") {
      const trimmed = text.trim();
      if (!NUMBER_PATTERN.test(trimmed)) {
        return { field: field.name, message: `${field.name}는 숫자여야 합니다` };
      }
      changes[field.name] = Number(trimmed);
    } else {
      changes[field.name] = text.trim();
    }
  }
  return { changes };
}
