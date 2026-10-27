import {
  MeterEngineBatchError,
  type IngestEventBatchResult,
  type IngestEventBatchResponse,
  type IngestEventInput,
} from "@meterengine/sdk";

import { toIngestEventInput, type CsvEventRow } from "./csv.ts";

export type RowResult = {
  line: number;
  transaction_id: string;
  tag: "NEW" | "DUP" | "REJ" | "SKIP" | "UNKNOWN" | "NOT_SENT";
  code?: string;
  message?: string;
};

const RESULT_TAGS: Record<IngestEventBatchResult["result"], RowResult["tag"]> = {
  stored: "NEW",
  duplicate: "DUP",
  rejected: "REJ",
};

export function toRowResults(
  rows: CsvEventRow[],
  results: IngestEventBatchResult[],
  attemptedCount = rows.filter((row) => row.error === undefined).length,
): RowResult[] {
  const resultsByIndex = new Map(results.map((result) => [result.index, result]));
  const rowResults: RowResult[] = [];
  let sentIndex = 0;
  for (const row of rows) {
    if (row.error !== undefined) {
      rowResults.push({ line: row.line, transaction_id: row.transaction_id, tag: "SKIP", message: row.error });
      continue;
    }
    const result = resultsByIndex.get(sentIndex);
    const isAttempted = sentIndex < attemptedCount;
    sentIndex += 1;
    if (result === undefined) {
      rowResults.push({
        line: row.line,
        transaction_id: row.transaction_id,
        tag: isAttempted ? "UNKNOWN" : "NOT_SENT",
        message: isAttempted
          ? "접수 결과를 받지 못했지만 서버가 접수했을 수 있습니다. 접수 여부를 확인하고, 재전송이 필요하면 같은 transaction_id를 유지하세요."
          : "이 행은 전송하지 않았습니다. 앞선 오류를 해결한 뒤 다시 보내세요.",
      });
      continue;
    }
    const rowResult: RowResult = { line: row.line, transaction_id: row.transaction_id, tag: RESULT_TAGS[result.result] };
    if (result.error !== undefined) {
      rowResult.code = result.error.code;
      rowResult.message = result.error.message;
    }
    rowResults.push(rowResult);
  }
  return rowResults;
}

export type CsvRowSendResult = { ok: true; results: RowResult[] } | { ok: false; error: unknown; results: RowResult[] };

export async function sendCsvRows(
  rows: CsvEventRow[],
  send: (events: IngestEventInput[]) => Promise<IngestEventBatchResponse>,
): Promise<CsvRowSendResult> {
  const sendable = rows.filter((row) => row.error === undefined);
  if (sendable.length === 0) {
    return { ok: true, results: toRowResults(rows, [], 0) };
  }
  try {
    const response = await send(sendable.map(toIngestEventInput));
    return { ok: true, results: toRowResults(rows, response.results) };
  } catch (error) {
    if (!(error instanceof MeterEngineBatchError)) {
      return { ok: false, error, results: toRowResults(rows, [], 0) };
    }
    const attemptedCount = error.failedBatch === undefined
      ? sendable.length
      : error.failedBatch.startIndex + error.failedBatch.size;
    return { ok: false, error, results: toRowResults(rows, error.results, attemptedCount) };
  }
}
