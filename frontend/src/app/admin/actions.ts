"use server";

import { revalidatePath } from "next/cache";
import { credentialFieldErrors } from "@/lib/auth/credentials";
import { listOrganizations, operatorRequest, type LiveEnvironmentApplicationResponse } from "@/lib/api/operator";
import { toDisplayMessage } from "@/lib/api/transport";
import { requireOperator } from "@/lib/auth/session";
import type { ActionState, OrganizationResponse, UserAccountResponse } from "@/lib/auth/types";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const REJECTION_REASON_MAX_LENGTH = 500;

export type CreateOperatorField = "name" | "email" | "password";
export type CreateOperatorState = {
  message?: string;
  fieldErrors?: Partial<Record<CreateOperatorField, string>>;
  isCreated?: boolean;
  isUnconfirmed?: boolean;
};

export async function createOperatorAction(_previous: CreateOperatorState, data: FormData): Promise<CreateOperatorState> {
  await requireOperator();
  const name = typeof data.get("name") === "string" ? String(data.get("name")).trim() : "";
  const email = typeof data.get("email") === "string" ? String(data.get("email")).trim() : "";
  const password = typeof data.get("password") === "string" ? String(data.get("password")) : "";
  const fieldErrors: Partial<Record<CreateOperatorField, string>> = credentialFieldErrors({ name, email, password });
  if (Object.keys(fieldErrors).length) return { message: "입력한 내용을 확인하세요.", fieldErrors };
  const result = await operatorRequest<UserAccountResponse>("/v1/user-accounts", { method: "POST", body: { name, email, password } });
  if (!result.ok) {
    if (result.error.code === "email_already_exists") return {
      message: "이미 가입한 이메일이에요. 소속 조직이 없는 계정이면 사용자 목록에서 플랫폼 운영자로 바꾸세요.",
      fieldErrors: { email: "다른 이메일을 입력하거나 그 계정의 권한을 바꾸세요." },
    };
    if (result.error.status === 0 || result.error.code === "malformed_response") {
      revalidatePath("/admin/users");
      return { isUnconfirmed: true, message: "생성 결과를 확인하지 못했어요. 목록을 새로 조회해 같은 이메일의 계정이 생겼는지 확인하세요." };
    }
    const errors = result.error.errors?.filter(error => ["name", "email", "password"].includes(error.field));
    return {
      message: errors?.length ? "입력한 내용을 확인하세요." : toDisplayMessage(result.error),
      fieldErrors: errors?.length ? Object.fromEntries(errors.map(error => [error.field, error.message])) : undefined,
    };
  }
  revalidatePath("/admin/users");
  return { isCreated: true, message: "운영자 계정을 만들었어요." };
}

export async function reviewSignupAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  await requireOperator();
  const id = data.get("id");
  const status = data.get("status");
  if (typeof id !== "string" || !UUID_PATTERN.test(id) || (status !== "active" && status !== "rejected")) return { message: "조직 정보를 읽지 못했어요. 목록을 다시 조회해 주세요." };
  const current = await listOrganizations("pending");
  if (!current.ok) return { message: toDisplayMessage(current.error) };
  if (!current.data.organizations.some(org => org.id === id && org.status === "pending")) return { message: "가입 대기 중인 조직이 아니에요. 목록을 다시 조회하세요." };
  const result = await operatorRequest<OrganizationResponse>(`/v1/organizations/${id}/status`, { method: "PUT", body: { status } });
  if (!result.ok) return { message: toDisplayMessage(result.error) };
  revalidatePath("/admin/organizations");
  return { done: true, message: status === "active" ? "가입을 승인했어요." : "가입을 거절했어요." };
}

export async function updateRoleAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  await requireOperator();
  const id = data.get("id");
  const role = data.get("role");
  if (typeof id !== "string" || !UUID_PATTERN.test(id) || (role !== "member" && role !== "operator")) return { message: "사용자 정보를 읽지 못했어요. 목록을 다시 조회한 뒤 다시 시도해 주세요." };
  const result = await operatorRequest<UserAccountResponse>(`/v1/user-accounts/${id}/role`, { method: "PUT", body: { role } });
  if (!result.ok) return { message: result.error.code === "operator_membership_conflict" ? "조직에 소속된 계정은 플랫폼 운영자로 바꿀 수 없어요." : toDisplayMessage(result.error) };
  revalidatePath("/", "layout");
  return { done: true, message: "사용자 역할을 바꿨어요." };
}

// isStale은 다른 운영자가 먼저 검토한 신청이다. 목록을 새로 불러 창을 닫는다.
export type LiveEnvironmentReviewState = ActionState & { isStale?: boolean };

export async function reviewLiveEnvironmentApplicationAction(_prev: LiveEnvironmentReviewState, data: FormData): Promise<LiveEnvironmentReviewState> {
  await requireOperator();
  const id = data.get("id");
  const status = data.get("status");
  const reason = typeof data.get("rejection_reason") === "string" ? String(data.get("rejection_reason")).trim() : "";
  if (typeof id !== "string" || !UUID_PATTERN.test(id) || (status !== "approved" && status !== "rejected")) return { message: "신청 정보를 읽지 못했어요. 목록을 다시 조회해 주세요." };
  if (status === "rejected" && !reason) return { message: "거절 사유를 입력하세요." };
  if (reason.length > REJECTION_REASON_MAX_LENGTH) return { message: `거절 사유는 ${REJECTION_REASON_MAX_LENGTH}자 이내로 입력하세요.` };
  const result = await operatorRequest<LiveEnvironmentApplicationResponse>(`/v1/live-environment-applications/${id}/status`, { method: "PUT", body: status === "rejected" ? { status, rejection_reason: reason } : { status } });
  if (!result.ok) {
    if (result.error.code === "live_environment_application_already_reviewed") {
      revalidatePath("/admin", "layout");
      return { isStale: true, message: "이미 검토를 마친 신청이에요. 목록을 새로 불러왔어요." };
    }
    return { message: toDisplayMessage(result.error) };
  }
  revalidatePath("/admin", "layout");
  return { done: true, message: status === "approved" ? "실제 환경 이용을 승인했어요." : "신청을 거절했어요." };
}
