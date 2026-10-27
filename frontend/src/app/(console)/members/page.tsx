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
      ...(member.organization_role !== "owner" ? [{ title: "권한 변경", operation: "updateMemberScopes", description: `${member.name} (${member.email})의 이 도입사 업무 권한을 변경합니다. 다음 요청부터 적용되며 다른 도입사의 권한은 바뀌지 않습니다.`, fields: [id, scopeField(member.scopes)], label: "권한 저장" }] : []),
      { title: "역할 변경", operation: "updateMemberRole", description: `${member.name} (${member.email})의 대표관리자로 지정하거나 전체 관리자로 변경합니다. 개별 권한은 권한 변경에서 정합니다. 마지막 대표관리자의 권한은 없앨 수 없습니다.`, fields: [id, { name: "organization_role", label: "도입사 역할", value: member.organization_role === "member" ? "" : member.organization_role, options: [{ value: "owner", label: "대표관리자: 사용자와 초대 관리 포함" }, { value: "admin", label: "관리자: 과금 업무 운영" }] }], label: "역할 변경" },
      { title: "소속 보관", operation: "archiveMember", description: `${member.name} (${member.email})의 이 도입사 접근을 중단합니다. 기존 업무와 감사 기록은 남습니다. 마지막 대표관리자는 보관할 수 없습니다.`, fields: [id], label: "접근 중단하고 보관", isDanger: true },
    ];
  }
  const invitationActions: WorkspaceRowActions = {};
  if (invitations?.ok) for (const invitation of invitations.data.invitations) {
    if (invitation.status !== "pending") continue;
    invitationActions[invitation.id] = [{ title: "초대 취소", operation: "revokeOrganizationInvitation", description: `${invitation.email}의 초대 링크를 더 이상 사용할 수 없게 합니다.`, fields: [{ name: "id", label: "", type: "hidden", value: invitation.id }], isDanger: true }];
  }
  return <>
    <WorkspaceHeader title="사용자 관리" kicker="도입사 설정" description="함께 일할 사용자를 추가하고, 사용자별 업무 권한과 접근을 관리하세요."><Link href="/audit">변경 이력 보기</Link></WorkspaceHeader>
    <section className="workspace-panel"><h2>함께 일할 사용자 추가</h2><p className="muted">대표관리자는 사용자와 초대를 관리합니다. 조회, 수집과 전체 관리 권한을 사용자별로 정하며 새 사용자의 기본값은 조회입니다. MeterEngine 플랫폼 운영자 권한과는 별개입니다.</p>
      {isOwner ? <div className="workspace-actions">
        <ActionPanel title="계정 직접 생성" operation="createMember" description="새 사용자 계정을 만들고 업무 권한을 지정합니다. 본인이 처음 로그인해 이용 안내에 동의한 뒤 업무를 시작할 수 있습니다. 이미 가입한 이메일은 초대로 연결하세요." label="사용자 계정 생성" fields={[{ name: "email", label: "사용자 이메일", type: "email", maxLength: 254 }, { name: "name", label: "이름", maxLength: 255 }, { name: "password", label: "초기 비밀번호", type: "password", hint: "8자 이상, UTF-8 기준 72바이트 이하. 로그인 정보는 본인에게 안전하게 전달하세요.", maxLength: 72 }, scopeField()]} />
        <ActionPanel title="초대 링크 만들기" operation="createOrganizationInvitation" description="메일 발송 없이 링크를 복사해 전달합니다. 지정한 이메일로 48시간 안에 한 번 수락할 수 있습니다. 링크를 받은 사람이 새 계정을 설정하므로 초대할 사람에게만 전달하세요." label="초대 링크 발급" fields={[{ name: "email", label: "초대할 이메일", type: "email", maxLength: 254 }, scopeField()]} />
      </div> : <p className="field-hint">사용자 추가와 권한 변경은 대표관리자에게 요청하세요.</p>}
    </section>
    <section className="workspace-panel"><h2>도입사 사용자</h2><ArchiveFilter label="사용자 관리 상태" />{members.ok ? <WorkspaceTable rows={members.data.members} rowKey="user_account_id" columns={[{ key: "name", label: "이름" }, { key: "email", label: "이메일" }, { key: "organization_role", label: "도입사 역할", isStatus: true }, { key: "scopes", label: "업무 권한" }, { key: "status", label: "접근 상태", isStatus: true }, { key: "created_at", label: "소속 시각", isDate: true }, { key: "revoked_at", label: "보관 시각", isDate: true }]} rowActions={actions} empty={status === "archived" ? "보관된 소속이 없습니다. 소속을 보관하면 이 목록에서 확인할 수 있습니다." : status === "all" ? "등록된 도입사 사용자가 없습니다." : "사용 중인 소속이 없습니다. 보관 목록을 확인하거나 관리자를 추가하세요."} /> : <WorkspaceError message={members.error.message} />}</section>
    {isOwner && <section className="workspace-panel"><h2>초대 이력</h2>{invitations?.ok ? <WorkspaceTable rows={invitations.data.invitations} columns={[{ key: "email", label: "초대한 이메일" }, { key: "scopes", label: "참여 권한" }, { key: "status", label: "상태", isStatus: true, statusLabels: { pending: "초대 대기", accepted: "수락 완료", revoked: "초대 취소", expired: "만료" } }, { key: "created_at", label: "발급 시각", isDate: true }, { key: "expires_at", label: "만료 시각", isDate: true }, { key: "accepted_at", label: "수락 시각", isDate: true }]} rowActions={invitationActions} empty="발급한 초대가 없습니다. 링크를 만들거나 계정을 직접 생성하세요." /> : invitations && <WorkspaceError message={invitations.error.message} />}</section>}
  </>;
}
