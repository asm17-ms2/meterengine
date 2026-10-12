"use server";

import { revalidatePath } from "next/cache";

import type { FinalizeInvoicesState } from "@/app/(console)/billing/state";
import { toDisplayMessage } from "@/lib/api/client";
import { finalizeInvoices } from "@/lib/api/invoices";
import { isMonth } from "@/lib/month";

export async function finalizeInvoicesAction(
  _prev: FinalizeInvoicesState,
  formData: FormData,
): Promise<FinalizeInvoicesState> {
  const month = formData.get("month");
  if (typeof month !== "string" || !isMonth(month)) {
    return { status: "failed", message: "확정할 달을 특정하지 못했습니다." };
  }

  const result = await finalizeInvoices(month);
  if (!result.ok) {
    return { status: "failed", message: toDisplayMessage(result.error) };
  }

  revalidatePath("/billing");
  revalidatePath("/invoices");
  return { status: "succeeded", finalizedCount: result.data.finalized_count };
}
