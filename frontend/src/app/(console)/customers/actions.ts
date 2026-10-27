"use server";

import { revalidatePath } from "next/cache";
import { requireTenantSession } from "@/lib/auth/session";
import { mutationScopeError } from "@/lib/auth/mutation-scope";
import { hasScope } from "@/lib/auth/permissions";

import type {
  CustomerDeleteState,
  CustomerFormState,
} from "@/app/(console)/customers/state";
import { toDisplayMessage, type ApiError } from "@/lib/api/client";
import {
  createCustomer,
  deleteCustomer,
  updateCustomer,
} from "@/lib/api/customers";

const NAME_MAX_LENGTH = 255;
const BILLING_EMAIL_MAX_LENGTH = 254;
const BILLING_EMAIL_PATTERN = /^\S+@\S+\.\S+$/;
const BILLING_EMAIL_FORMAT_MESSAGE = "올바른 형식의 이메일 주소여야 합니다";

function readName(formData: FormData): string {
  const raw = formData.get("name");
  return typeof raw === "string" ? raw.trim() : "";
}

function validateName(name: string): string | null {
  if (name === "") return "고객명을 입력하세요";
  if (name.length > NAME_MAX_LENGTH) return `고객명은 ${NAME_MAX_LENGTH}자를 넘을 수 없습니다`;
  return null;
}

function readBillingEmail(formData: FormData): string | null {
  const raw = formData.get("billing_email");
  const trimmed = typeof raw === "string" ? raw.trim() : "";
  return trimmed === "" ? null : trimmed;
}

function validateBillingEmail(billingEmail: string | null): string | null {
  if (billingEmail === null) return null;
  if (billingEmail.length > BILLING_EMAIL_MAX_LENGTH) {
    return `청구 이메일은 ${BILLING_EMAIL_MAX_LENGTH}자를 넘을 수 없습니다`;
  }
  if (!BILLING_EMAIL_PATTERN.test(billingEmail)) return BILLING_EMAIL_FORMAT_MESSAGE;
  return null;
}

function validateForm(
  name: string,
  billingEmail: string | null,
): CustomerFormState | null {
  const nameMessage = validateName(name);
  if (nameMessage) return { status: "invalid", field: "name", message: nameMessage };
  const billingEmailMessage = validateBillingEmail(billingEmail);
  if (billingEmailMessage) {
    return { status: "invalid", field: "billingEmail", message: billingEmailMessage };
  }
  return null;
}

function toFailedState(error: ApiError): CustomerFormState {
  const billingEmailErrors =
    error.errors?.filter((fieldError) => fieldError.field === "billing_email") ?? [];
  if (billingEmailErrors.length > 0 && billingEmailErrors.length === error.errors?.length) {
    return { status: "invalid", field: "billingEmail", message: billingEmailErrors[0].message };
  }
  return { status: "failed", message: toDisplayMessage(error) };
}

export async function createCustomerAction(
  _prev: CustomerFormState,
  formData: FormData,
): Promise<CustomerFormState> {
  const session = await requireTenantSession();
  const scopeError = await mutationScopeError(formData, session.organization?.id);
  if (scopeError) return { status: "failed", message: scopeError };
  if (!hasScope(session.scopes, "ingest")) return { status: "failed", message: "고객 등록에 필요한 수집 권한이 없습니다. 대표관리자에게 권한을 요청하세요." };
  const name = readName(formData);
  const billingEmail = readBillingEmail(formData);
  const invalidState = validateForm(name, billingEmail);
  if (invalidState) return invalidState;

  const result = await createCustomer(name, billingEmail, String(formData.get("request_id") ?? ""));
  if (!result.ok) return toFailedState(result.error);

  revalidatePath("/customers");
  return { status: "done" };
}

export async function updateCustomerAction(
  _prev: CustomerFormState,
  formData: FormData,
): Promise<CustomerFormState> {
  const session = await requireTenantSession();
  const scopeError = await mutationScopeError(formData, session.organization?.id);
  if (scopeError) return { status: "failed", message: scopeError };
  if (!hasScope(session.scopes, "admin")) return { status: "failed", message: "이 작업을 수행할 관리 권한이 없습니다. 대표관리자에게 권한을 요청하세요." };
  const id = formData.get("id");
  if (typeof id !== "string" || id === "") {
    return { status: "failed", message: "고객을 특정하지 못했습니다." };
  }

  const name = readName(formData);
  const billingEmail = readBillingEmail(formData);
  const invalidState = validateForm(name, billingEmail);
  if (invalidState) return invalidState;

  const version = formData.get("version");
  if (typeof version !== "string" || !/^\d+$/.test(version) || !Number.isSafeInteger(Number(version))) return { status: "failed", message: "최신 고객 정보를 다시 조회한 뒤 수정하세요." };
  const result = await updateCustomer(id, name, billingEmail, Number(version));
  if (!result.ok) return toFailedState(result.error);

  revalidatePath("/customers");
  return { status: "done" };
}

export async function deleteCustomerAction(
  _prev: CustomerDeleteState,
  formData: FormData,
): Promise<CustomerDeleteState> {
  const session = await requireTenantSession();
  const scopeError = await mutationScopeError(formData, session.organization?.id);
  if (scopeError) return { status: "failed", message: scopeError };
  if (!hasScope(session.scopes, "admin")) return { status: "failed", message: "이 작업을 수행할 관리 권한이 없습니다. 대표관리자에게 권한을 요청하세요." };
  const id = formData.get("id");
  const name = formData.get("name");
  const customerName = typeof name === "string" ? name : "";
  if (typeof id !== "string" || id === "") {
    return { status: "failed", message: "고객을 특정하지 못했습니다." };
  }

  const result = await deleteCustomer(id);
  if (!result.ok) {
    if (result.error.code === "customer_has_events") {
      return { status: "rejected", name: customerName };
    }
    if (result.error.code === "customer_not_found") {
      revalidatePath("/customers");
      return { status: "gone", name: customerName };
    }
    return { status: "failed", message: toDisplayMessage(result.error) };
  }

  revalidatePath("/customers");
  return { status: "done" };
}
