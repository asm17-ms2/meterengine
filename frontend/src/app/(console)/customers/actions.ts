"use server";

import { revalidatePath } from "next/cache";

import type {
  CustomerDeleteState,
  CustomerFormState,
} from "@/app/(console)/customers/state";
import type { ApiError } from "@/lib/api/client";
import {
  createCustomer,
  deleteCustomer,
  updateCustomer,
} from "@/lib/api/customers";

const NAME_MAX_LENGTH = 255;

function readName(formData: FormData): string {
  const raw = formData.get("name");
  return typeof raw === "string" ? raw.trim() : "";
}

function validateName(name: string): string | null {
  if (name === "") return "고객명을 입력하세요";
  if (name.length > NAME_MAX_LENGTH) return `고객명은 ${NAME_MAX_LENGTH}자를 넘을 수 없습니다`;
  return null;
}

function toSaveFailureMessage(error: ApiError): string {
  switch (error.code) {
    case "validation_error":
      return `고객명을 확인해주세요. ${NAME_MAX_LENGTH}자 이내여야 합니다.`;
    case "customer_not_found":
      return "이미 삭제된 고객입니다. 목록을 새로 고쳐주세요.";
    case "unknown_organization":
      return "도입사를 찾을 수 없습니다. 설정을 확인해주세요.";
    case "network_error":
      return error.message;
    default:
      return "저장하지 못했습니다. 잠시 후 다시 시도해주세요.";
  }
}

export async function createCustomerAction(
  _prev: CustomerFormState,
  formData: FormData,
): Promise<CustomerFormState> {
  const name = readName(formData);
  const invalidMessage = validateName(name);
  if (invalidMessage) return { status: "invalid", message: invalidMessage };

  const result = await createCustomer(name);
  if (!result.ok) {
    return { status: "failed", message: toSaveFailureMessage(result.error) };
  }

  revalidatePath("/customers");
  return { status: "done" };
}

export async function updateCustomerAction(
  _prev: CustomerFormState,
  formData: FormData,
): Promise<CustomerFormState> {
  const id = formData.get("id");
  if (typeof id !== "string" || id === "") {
    return { status: "failed", message: "고객을 특정하지 못했습니다." };
  }

  const name = readName(formData);
  const invalidMessage = validateName(name);
  if (invalidMessage) return { status: "invalid", message: invalidMessage };

  const result = await updateCustomer(id, name);
  if (!result.ok) {
    return { status: "failed", message: toSaveFailureMessage(result.error) };
  }

  revalidatePath("/customers");
  return { status: "done" };
}

export async function deleteCustomerAction(
  _prev: CustomerDeleteState,
  formData: FormData,
): Promise<CustomerDeleteState> {
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
    return {
      status: "failed",
      message:
        result.error.code === "network_error"
          ? result.error.message
          : "삭제하지 못했습니다. 잠시 후 다시 시도해주세요.",
    };
  }

  revalidatePath("/customers");
  return { status: "done" };
}
