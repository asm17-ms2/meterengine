"use server";

import { revalidatePath } from "next/cache";
import { requireTenantSession } from "@/lib/auth/session";
import { hasScope } from "@/lib/auth/permissions";
import { serverSend, toDisplayMessage } from "@/lib/api/client";
import { config } from "@/lib/config";
import type { CreatedInvoiceViewLink, InvoiceViewLink } from "@/lib/invoice-view-links";

const IDENTIFIER = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function createInvoiceViewLink(invoiceId: string, expiresInDays: number, allowPaymentRetry = false) {
  const session = await requireTenantSession();
  if (!hasScope(session.scopes, "admin")) return { ok: false as const, message: "고객 조회 링크를 관리할 권한이 없습니다." };
  if (!IDENTIFIER.test(invoiceId) || !Number.isInteger(expiresInDays) || expiresInDays < 1 || expiresInDays > 90 || typeof allowPaymentRetry !== "boolean") {
    return { ok: false as const, message: "사용 기간을 1일부터 90일 사이로 선택해 주세요." };
  }
  const result = await serverSend<CreatedInvoiceViewLink>(config.apiBaseUrl, `/v1/invoices/${invoiceId}/view-links`, {
    method: "POST", body: { expires_in_days: expiresInDays, allow_payment_retry: allowPaymentRetry },
  });
  if (!result.ok) return { ok: false as const, message: toDisplayMessage(result.error) };
  revalidatePath(`/payments/${invoiceId}`);
  return { ok: true as const, data: result.data };
}

export async function revokeInvoiceViewLink(invoiceId: string, linkId: string) {
  const session = await requireTenantSession();
  if (!hasScope(session.scopes, "admin")) return { ok: false as const, message: "고객 조회 링크를 관리할 권한이 없습니다." };
  if (!IDENTIFIER.test(invoiceId) || !IDENTIFIER.test(linkId)) {
    return { ok: false as const, message: "폐기할 링크를 확인하지 못했습니다. 목록을 새로 조회해 주세요." };
  }
  const result = await serverSend<InvoiceViewLink>(config.apiBaseUrl, `/v1/invoices/${invoiceId}/view-links/${linkId}/revoke`, { method: "POST" });
  if (!result.ok) return { ok: false as const, message: toDisplayMessage(result.error) };
  revalidatePath(`/payments/${invoiceId}`);
  return { ok: true as const, data: result.data };
}
