"use server";

import { revalidatePath } from "next/cache";
import { credentialFieldErrors } from "@/lib/auth/credentials";
import { listOrganizations, operatorRequest } from "@/lib/api/operator";
import { toDisplayMessage } from "@/lib/api/transport";
import { requireOperator } from "@/lib/auth/session";
import type { ActionState, OrganizationResponse, UserAccountResponse } from "@/lib/auth/types";
import { isAccessScope } from "@/lib/auth/permissions";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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
      message: "이미 가입한 이메일입니다. 기존 계정은 사용자 목록에서 플랫폼 운영자로 변경하세요.",
      fieldErrors: { email: "다른 이메일을 입력하거나 기존 계정의 권한을 변경하세요." },
    };
    if (result.error.status === 0 || result.error.code === "malformed_response") {
      revalidatePath("/admin/users");
      return { isUnconfirmed: true, message: "생성 결과를 확인하지 못했습니다. 목록을 새로 조회해 같은 이메일의 계정이 생겼는지 확인하세요." };
    }
    const errors = result.error.errors?.filter(error => ["name", "email", "password"].includes(error.field));
    return {
      message: errors?.length ? "입력한 내용을 확인하세요." : toDisplayMessage(result.error),
      fieldErrors: errors?.length ? Object.fromEntries(errors.map(error => [error.field, error.message])) : undefined,
    };
  }
  revalidatePath("/admin/users");
  return { isCreated: true, message: "운영자 계정을 만들었습니다." };
}

export async function reviewSignupAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  await requireOperator();
  const id = data.get("id");
  const status = data.get("status");
  if (typeof id !== "string" || !UUID_PATTERN.test(id) || (status !== "active" && status !== "rejected")) return { message: "승인하거나 거절할 도입사를 확인하세요." };
  const current = await listOrganizations("pending");
  if (!current.ok) return { message: toDisplayMessage(current.error) };
  if (!current.data.organizations.some(org => org.id === id && org.status === "pending")) return { message: "승인 대기 중인 도입사가 아닙니다. 목록을 새로고침하세요." };
  const result = await operatorRequest<OrganizationResponse>(`/v1/organizations/${id}/status`, { method: "PUT", body: { status } });
  if (!result.ok) return { message: toDisplayMessage(result.error) };
  revalidatePath("/admin/organizations");
  return { done: true, message: status === "active" ? "가입을 승인했습니다." : "가입을 거절했습니다." };
}

export async function updateRoleAction(_prev: ActionState, data: FormData): Promise<ActionState> {
  await requireOperator();
  const id = data.get("id");
  const role = data.get("role");
  if (typeof id !== "string" || !UUID_PATTERN.test(id) || (role !== "member" && role !== "operator")) return { message: "사용자와 변경할 역할을 확인하세요." };
  const result = await operatorRequest<UserAccountResponse>(`/v1/user-accounts/${id}/role`, { method: "PUT", body: { role } });
  if (!result.ok) return { message: toDisplayMessage(result.error) };
  revalidatePath("/", "layout");
  return { done: true, message: "사용자 역할을 변경했습니다." };
}

export async function attachOrganizationAction(_previous: ActionState, data: FormData): Promise<ActionState> {
  await requireOperator();
  const userId = data.get("user_account_id");
  const organizationId = data.get("organization_id");
  const scopes = data.getAll("scopes");
  if (typeof userId !== "string" || !UUID_PATTERN.test(userId) || typeof organizationId !== "string" || !UUID_PATTERN.test(organizationId)) return { message: "연결할 사용자와 도입사를 확인하세요." };
  if (!scopes.length || !scopes.every(isAccessScope)) return { message: "조회, 수집과 전체 관리 중 권한을 하나 이상 선택하세요." };
  const result = await operatorRequest(`/v1/organizations/${organizationId}/members`, { method: "POST", body: { user_account_id: userId, scopes } });
  if (!result.ok) return { message: toDisplayMessage(result.error) };
  revalidatePath("/admin/users");
  revalidatePath("/admin/organizations");
  return { done: true, message: "도입사 소속과 업무 권한을 연결했습니다." };
}
