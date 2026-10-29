import type { IngestEventInput } from "@meterengine/sdk";

export const CSV_REQUIRED_COLUMNS = ["transaction_id", "customer_id", "event_type", "timestamp", "properties"] as const;

const PROPERTIES_ERROR = "properties가 JSON 객체가 아닙니다";

export type CsvEventRow = {
  line: number;
  transaction_id: string;
  customer_id: string;
  event_type: string;
  timestamp: string;
  properties_text: string;
  properties?: Record<string, unknown>;
  note: string;
  error?: string;
};

type CsvRecord = { line: number; fields: string[] };

function readRecords(text: string): { records: CsvRecord[] } | { unclosedQuoteLine: number } {
  const records: CsvRecord[] = [];
  let fields: string[] = [];
  let field = "";
  let isQuoted = false;
  let isFieldStart = true;
  let hasQuotedField = false;
  let line = 1;
  let recordLine = 1;
  let quoteLine = 1;

  const endField = () => {
    fields.push(field);
    field = "";
    isFieldStart = true;
  };

  const endRecord = () => {
    endField();
    const isBlank = fields.length === 1 && fields[0] === "" && !hasQuotedField;
    if (!isBlank) {
      records.push({ line: recordLine, fields });
    }
    fields = [];
    hasQuotedField = false;
  };

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (isQuoted) {
      if (char === '"') {
        if (text[index + 1] === '"') {
          field += '"';
          index += 1;
        } else {
          isQuoted = false;
        }
      } else {
        if (char === "\n") {
          line += 1;
        }
        field += char;
      }
    } else if (char === '"' && isFieldStart) {
      isQuoted = true;
      isFieldStart = false;
      hasQuotedField = true;
      quoteLine = line;
    } else if (char === ",") {
      endField();
    } else if (char === "\n") {
      endRecord();
      line += 1;
      recordLine = line;
    } else {
      field += char;
      isFieldStart = false;
    }
  }
  if (isQuoted) {
    return { unclosedQuoteLine: quoteLine };
  }
  if (field !== "" || fields.length > 0 || hasQuotedField) {
    endRecord();
  }
  return { records };
}

function parseProperties(text: string): Record<string, unknown> | undefined {
  try {
    const value: unknown = JSON.parse(text);
    if (typeof value === "object" && value !== null && !Array.isArray(value)) {
      return value as Record<string, unknown>;
    }
    return undefined;
  } catch {
    return undefined;
  }
}

export function parseEventCsv(text: string): { rows: CsvEventRow[] } | { error: string } {
  const normalized = text.replace(/^\uFEFF/, "").replace(/\r\n?/g, "\n");
  const read = readRecords(normalized);
  if ("unclosedQuoteLine" in read) {
    return { error: `${read.unclosedQuoteLine}번째 줄에서 연 따옴표가 닫히지 않았습니다` };
  }
  const [header, ...records] = read.records;
  if (header === undefined) {
    return { error: "CSV에 헤더가 없습니다" };
  }
  const columns = header.fields.map((name) => name.trim());
  const missing = CSV_REQUIRED_COLUMNS.filter((name) => !columns.includes(name));
  if (missing.length > 0) {
    return { error: `필수 컬럼이 없습니다: ${missing.join(", ")}` };
  }

  const valueOf = (record: CsvRecord, name: string): string => {
    const index = columns.indexOf(name);
    return index === -1 ? "" : (record.fields[index] ?? "");
  };

  const rows = records.map((record): CsvEventRow => {
    const propertiesText = valueOf(record, "properties");
    const properties = parseProperties(propertiesText);
    const row: CsvEventRow = {
      line: record.line,
      transaction_id: valueOf(record, "transaction_id"),
      customer_id: valueOf(record, "customer_id"),
      event_type: valueOf(record, "event_type"),
      timestamp: valueOf(record, "timestamp"),
      properties_text: propertiesText,
      note: valueOf(record, "note"),
    };
    return properties === undefined ? { ...row, error: PROPERTIES_ERROR } : { ...row, properties };
  });
  return { rows };
}

export function toIngestEventInput(row: CsvEventRow): IngestEventInput {
  return {
    transaction_id: row.transaction_id,
    customer_id: row.customer_id,
    type: row.event_type,
    timestamp: row.timestamp,
    properties: row.properties ?? {},
  };
}
