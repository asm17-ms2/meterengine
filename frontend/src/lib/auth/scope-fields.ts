import type { WorkspaceField } from "@/components/workspace/ActionPanel";
import { ACCESS_SCOPE_OPTIONS } from "./permissions";

export function scopeField(scopes: readonly string[] = ["read"], label = "업무 권한"): WorkspaceField {
  return {
    name: "scopes", label, isMultiple: true, value: scopes.join(","),
    hint: "여러 권한을 함께 고를 수 있어요. 전체 관리는 조회와 수집을 포함하지만 결제 요청과 취소는 결제 권한을 따로 골라야 해요.",
    options: ACCESS_SCOPE_OPTIONS,
  };
}
