import type { Metadata } from "next";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { ArchiveFilter } from "@/components/workspace/ArchiveFilter";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable, type WorkspaceRowActions } from "@/components/workspace/WorkspaceView";
import { listOrganizationInvitations, listOrganizationMembers } from "@/lib/api/management";
import { requireTenantSession } from "@/lib/auth/session";
import { hasScope } from "@/lib/auth/permissions";
import { scopeField } from "@/lib/auth/scope-fields";
import { readArchiveStatus } from "@/lib/list-query";

export const metadata: Metadata = { title: "사용자 관리" };

export default async function MembersPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const session = await requireTenantSession();
  const isOwner = session.organization_role === "owner" && hasScope(session.scopes, "admin");
  const status = readArchiveStatus((await searchParams).status);
  const [members, invitations] = await Promise.all([listOrganizationMembers(status), isOwner ? listOrganizationInvitations() : Promise.resolve(null)]);
  const actions: WorkspaceRowActions = {};
  if (members.ok && isOwner) for (const member of members.data.members) {
    if (member.status !== "active") continue;
    const id = { name: "id", label: "", type: "hidden" as const, value: member.user_account_id };
    actions[member.user_account_id] = [
      ...(member.organization_role !== "owner" ? [{ title: "권한 변경", operation: "updateMemberScopes", description: `${member.name} (${member.email})의 업무 권한을 바꿔요. 다음 요청부터 적용되고, 다른 조직에서의 권한은 바뀌지 않아요.`, fields: [id, scopeField(member.scopes)], label: "권한 저장" }] : []),
      { title: "역할 변경", operation: "updateMemberRole", description: `${member.name} (${member.email})의 역할을 대표관리자나 관리자로 바꿔요. 어느 역할이든 업무 권한이 전체 관리(조회, 수집 포함)로 바뀌고, 지금 권한은 남지 않아요. 마지막 대표관리자는 다른 역할로 바꿀 수 없어요.`, fields: [id, { name: "organization_role", label: "역할", value: member.organization_role === "member" ? "" : member.organization_role, options: [{ value: "owner", label: "대표관리자: 사용자와 초대 관리 포함" }, { value: "admin", label: "관리자: 전체 관리 권한으로 과금 업무 운영" }] }], label: "역할 변경" },
      { title: "소속 보관", operation: "archiveMember", description: `이 조직의 콘솔에서 ${member.name} (${member.email})의 접근을 막아요. 이 이메일로 보낸 대기 중인 초대도 취소돼요. 이 사용자가 한 업무와 감사 기록은 남아요. 마지막 대표관리자는 보관할 수 없어요.`, fields: [id], label: "접근 중단하고 보관", isDanger: true },
    ];
  }
  const invitationActions: WorkspaceRowActions = {};
  if (invitations?.ok) for (const invitation of invitations.data.invitations) {
    if (invitation.status !== "pending") continue;
    invitationActions[invitation.id] = [{ title: "초대 취소", operation: "revokeOrganizationInvitation", description: `${invitation.email}의 초대 링크를 더 이상 쓸 수 없게 해요.`, fields: [{ name: "id", label: "", type: "hidden", value: invitation.id }], isDanger: true }];
  }
  return <>
    <WorkspaceHeader title="사용자 관리" description="함께 일할 사용자를 추가하고, 사용자별 업무 권한과 접근을 관리하세요."><Link href="/audit?q=organization_">변경 이력 보기</Link></WorkspaceHeader>
    <section className="workspace-panel"><h2>함께 일할 사용자 추가</h2><p className="muted">사용자마다 조회, 수집, 전체 관리 권한을 정해요. 새 사용자의 기본 권한은 조회예요. 사용자와 초대는 대표관리자가 관리해요.</p>
      {isOwner ? <div className="workspace-actions">
        <ActionPanel title="계정 직접 생성" operation="createMember" description="새 사용자 계정을 만들고 업무 권한을 정해요. 만든 사용자는 처음 로그인할 때 약관과 개인정보 처리방침에 동의해야 업무를 시작할 수 있어요. 이미 가입한 이메일이면 초대 링크를 만드세요." label="사용자 계정 생성" fields={[{ name: "email", label: "사용자 이메일", type: "email", maxLength: 254 }, { name: "name", label: "이름", maxLength: 255 }, { name: "password", label: "초기 비밀번호", type: "password", hint: "8자 이상 72자 이하예요. 한글은 1자를 3자로 계산해요. 로그인 정보는 그 사용자에게 안전하게 전달하세요.", maxLength: 72 }, scopeField()]} />
        <ActionPanel title="초대 링크 만들기" operation="createOrganizationInvitation" description="메일은 보내지 않아요. 링크를 복사해 직접 전달하세요. 지정한 이메일로 48시간 안에 한 번 수락할 수 있어요. 같은 이메일로 다시 발급해도 이전 링크는 취소하기 전까지 쓸 수 있으니, 링크를 잃었다면 초대 이력에서 이전 초대를 먼저 취소하세요. 링크를 받은 사람이 새 계정을 설정하므로 초대할 사람에게만 전달하세요." label="초대 링크 발급" fields={[{ name: "email", label: "초대할 이메일", type: "email", maxLength: 254 }, scopeField()]} />
      </div> : <p className="field-hint">사용자 추가와 권한 변경은 대표관리자에게 요청하세요.</p>}
    </section>
    <section className="workspace-panel"><h2>사용자 목록</h2><ArchiveFilter label="사용자 관리 상태" />{members.ok ? <WorkspaceTable label="사용자 목록" rows={members.data.members} rowKey="user_account_id" columns={[{ key: "name", label: "이름" }, { key: "email", label: "이메일" }, { key: "organization_role", label: "역할", isStatus: true }, { key: "scopes", label: "업무 권한" }, { key: "status", label: "접근 상태", isStatus: true }, { key: "created_at", label: "소속 시각", isDate: true }, { key: "revoked_at", label: "보관 시각", isDate: true }]} rowActions={actions} empty={status === "archived" ? "보관된 소속이 없어요. 소속을 보관하면 여기에 보여요." : status === "all" ? "등록된 사용자가 없어요." : "사용 중인 소속이 없어요. 보관 목록을 확인하거나 사용자를 추가하세요."} /> : <WorkspaceError message={members.error.message} status={members.error.status} />}</section>
    {isOwner && <section className="workspace-panel"><h2>초대 이력</h2>{invitations?.ok ? <WorkspaceTable label="초대 이력" rows={invitations.data.invitations} columns={[{ key: "email", label: "초대한 이메일" }, { key: "scopes", label: "업무 권한" }, { key: "status", label: "상태", isStatus: true, statusLabels: { pending: "초대 대기", accepted: "수락 완료", revoked: "초대 취소", expired: "만료" } }, { key: "created_at", label: "발급 시각", isDate: true }, { key: "expires_at", label: "만료 시각", isDate: true }, { key: "accepted_at", label: "수락 시각", isDate: true }]} rowActions={invitationActions} empty="발급한 초대가 없어요. 초대 링크를 만들거나 계정을 직접 생성하세요." /> : invitations && <WorkspaceError message={invitations.error.message} status={invitations.error.status} />}</section>}
  </>;
}
