import type { WorkspaceField } from "@/components/workspace/ActionPanel";
import { ACCESS_SCOPE_OPTIONS } from "./permissions";

export function scopeField(scopes: readonly string[] = ["read"], label = "업무 권한"): WorkspaceField {
  return {
    name: "scopes", label, isMultiple: true, value: scopes.join(","),
    hint: "조회와 수집은 함께 선택할 수 있습니다. 전체 관리는 조회와 수집을 포함하며 플랫폼 운영자 권한은 주지 않습니다.",
    options: ACCESS_SCOPE_OPTIONS,
  };
}
