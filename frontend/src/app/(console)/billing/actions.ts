"use server";

import { revalidatePath } from "next/cache";
import { requireTenantSession } from "@/lib/auth/session";
import { mutationScopeError } from "@/lib/auth/mutation-scope";
import { hasScope } from "@/lib/auth/permissions";

import type { FinalizeInvoicesState } from "@/app/(console)/billing/state";
import { toDisplayMessage } from "@/lib/api/client";
import { finalizeInvoices } from "@/lib/api/invoices";
import { isMonth } from "@/lib/month";

export async function finalizeInvoicesAction(
  _prev: FinalizeInvoicesState,
  formData: FormData,
): Promise<FinalizeInvoicesState> {
  const session = await requireTenantSession();
  const scopeError = await mutationScopeError(formData, session.organization?.id);
  if (scopeError) return { status: "failed", message: scopeError };
  if (!hasScope(session.scopes, "admin")) return { status: "failed", message: "인보이스를 확정할 권한이 없어요. 대표관리자에게 전체 관리 권한을 요청하세요." };
  const month = formData.get("month");
  if (typeof month !== "string" || !isMonth(month)) {
    return { status: "failed", message: "확정할 달을 확인하지 못했어요. 화면을 새로 조회한 뒤 다시 시도해 주세요." };
  }

  const result = await finalizeInvoices(month);
  if (!result.ok) {
    return { status: "failed", message: toDisplayMessage(result.error) };
  }

  revalidatePath("/billing");
  revalidatePath("/invoices");
  return { status: "succeeded", finalizedCount: result.data.finalized_count, failedCount: result.data.failed_count, supplyAmount: result.data.supply_amount, taxAmount: result.data.tax_amount, totalAmount: result.data.total_amount, invoices: result.data.invoices };
}
