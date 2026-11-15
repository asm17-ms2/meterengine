import type { ApiError } from "@/lib/api/transport";

export type InvitationProblem = { description: string; nextStep: string };

// 서버는 만료, 수락, 취소를 invitation_unavailable 하나로 돌려준다.
const INVITATION_PROBLEMS: Record<string, InvitationProblem> = {
  invitation_unavailable: { description: "만료됐거나 이미 수락했거나 취소된 초대예요.", nextStep: "이미 수락했다면 로그인하세요. 아니라면 대표관리자에게 새 초대를 요청하세요." },
  invitation_not_found: { description: "초대 링크를 찾을 수 없어요.", nextStep: "받은 메일의 링크를 다시 열어 보세요. 그래도 열리지 않으면 대표관리자에게 새 초대를 요청하세요." },
  organization_not_active: { description: "초대한 조직을 지금 이용할 수 없어요.", nextStep: "초대한 조직의 대표관리자에게 확인하세요." },
};

export const INVITATION_NOT_FOUND = INVITATION_PROBLEMS.invitation_not_found;
const INVITATION_LOOKUP_FAILED: InvitationProblem = { description: "초대 링크를 확인하지 못했어요.", nextStep: "잠시 뒤 이 페이지를 다시 열어 주세요." };

export function invitationProblem(error: ApiError): InvitationProblem {
  return INVITATION_PROBLEMS[error.code] ?? INVITATION_LOOKUP_FAILED;
}

export function knownInvitationProblem(error: ApiError): InvitationProblem | undefined {
  return INVITATION_PROBLEMS[error.code];
}

export function invitationProblemMessage({ description, nextStep }: InvitationProblem) {
  return `${description} ${nextStep}`;
}
