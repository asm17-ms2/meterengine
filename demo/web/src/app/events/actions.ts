"use server";

import type { IngestEventInput, IngestEventResponse } from "@meterengine/sdk";

import { parseEventProperties } from "@/lib/event-form";
import { assertLocalRequest } from "@/lib/local-request";
import { clientFor, toErrorView, type ErrorView } from "@/lib/meter-engine";

export type EventSendState =
  | { status: "idle" }
  | { status: "invalid"; field: string; message: string }
  | { status: "done"; response: IngestEventResponse }
  | { status: "failed"; error: ErrorView };

function readText(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

export async function ingestEventAction(_previous: EventSendState, formData: FormData): Promise<EventSendState> {
  await assertLocalRequest();
  const parsed = parseEventProperties(readText(formData, "properties"));
  if ("message" in parsed) {
    return { status: "invalid", field: "properties", message: parsed.message };
  }
  const transactionId = readText(formData, "transaction_id");
  const input: IngestEventInput = {
    customer_id: readText(formData, "customer_id"),
    type: readText(formData, "type"),
    timestamp: readText(formData, "timestamp"),
    properties: parsed.properties,
    transaction_id: transactionId === "" ? undefined : transactionId,
  };
  try {
    const response = await clientFor(readText(formData, "target")).ingestEvent(input);
    return { status: "done", response };
  } catch (error) {
    return { status: "failed", error: toErrorView(error) };
  }
}
