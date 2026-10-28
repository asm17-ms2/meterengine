const SUCCESS_MESSAGES: Partial<Record<string, string>> = {
  createMember: "사용자 계정을 만들었습니다.",
  updateMemberRole: "사용자 역할을 바꿨습니다.",
  updateMemberScopes: "사용자 권한을 저장했습니다.",
  revokeOrganizationInvitation: "초대를 취소했습니다.",
  deleteApiKey: "API 키를 폐기했습니다.",
  updateApiKeyScopes: "API 키 권한을 저장했습니다.",
};

export function successMessage(operation: string): string {
  return SUCCESS_MESSAGES[operation] ?? "처리했습니다.";
}
