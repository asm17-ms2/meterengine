"use server";

import { parseEventCsv } from "@/lib/csv";
import { assertLocalRequest } from "@/lib/local-request";
import { clientFor, toErrorView, type ErrorView } from "@/lib/meter-engine";
import { sendCsvRows, type RowResult } from "@/lib/send-results";

export type CsvSendState =
  | { status: "idle" }
  | { status: "done"; results: RowResult[] }
  | { status: "failed"; error: ErrorView; results: RowResult[] };

function readText(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

export async function sendEventCsvAction(formData: FormData): Promise<CsvSendState> {
  await assertLocalRequest();
  const target = readText(formData, "target");
  const parsed = parseEventCsv(readText(formData, "csv"));
  if ("error" in parsed) {
    return { status: "failed", error: { kind: "unknown", message: parsed.error }, results: [] };
  }
  const sent = await sendCsvRows(parsed.rows, (events) => clientFor(target, { maxRetries: 0 }).ingestEventBatch(events));
  return sent.ok
    ? { status: "done", results: sent.results }
    : { status: "failed", error: toErrorView(sent.error), results: sent.results };
}
