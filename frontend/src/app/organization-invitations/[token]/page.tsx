import Link from "next/link";
import type { Metadata } from "next";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { InvitationForm } from "@/components/auth/InvitationForm";
import { LogoutButton } from "@/components/auth/SessionControls";
import { requestApi } from "@/lib/api/transport";
import { readSessionToken } from "@/lib/auth/session";
import { formatKstDateTime } from "@/lib/format";
import { scopeSummary } from "@/lib/auth/permissions";
import type { SessionResponse } from "@/lib/auth/types";
import { reviewConsentsForInvitation, signInForInvitation } from "./actions";

export const metadata: Metadata = { title: "도입사 초대", robots: { index: false, follow: false }, referrer: "no-referrer" };

type InvitationPreview = { organization_name: string; email: string; expires_at: string; account_exists: boolean; scopes: string[] };

export default async function OrganizationInvitationPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!/^me_invite_[A-Za-z0-9]{32}$/.test(token)) return <AuthFrame variant="join" title="초대 링크 확인" description="올바르지 않은 초대 링크입니다."><Link href="/login">로그인으로</Link></AuthFrame>;
  const result = await requestApi<InvitationPreview>(`/v1/organization-invitations/${encodeURIComponent(token)}`);
  if (!result.ok) return <AuthFrame variant="join" title="초대 링크 확인" description={result.error.message}><p>만료되거나 취소된 링크라면 대표관리자에게 새 초대를 요청하세요.</p><Link href="/login">로그인으로</Link></AuthFrame>;
  const invitation = result.data;
  const sessionToken = await readSessionToken();
  const current = sessionToken ? await requestApi<SessionResponse>("/v1/sessions/current", { token: sessionToken }) : null;
  const isMatchingAccount = current?.ok && current.data.user_account.email.toLowerCase() === invitation.email.toLowerCase();
  return <AuthFrame variant="join" currentStep={1} title={`${invitation.organization_name}에 참여`} description={`${invitation.email} 계정의 사용자로 초대됐습니다.`}>
    <p>참여 권한: {scopeSummary(invitation.scopes)}</p>
    <p className="field-hint">초대는 {formatKstDateTime(invitation.expires_at)} KST까지 한 번 수락할 수 있습니다.</p>
    {isMatchingAccount && current?.ok && current.data.consent_required ? <div className="workspace-form"><p>이 계정은 이용 안내 확인이 필요합니다. 동의를 마치면 초대 수락을 계속합니다.</p><form action={reviewConsentsForInvitation}><input type="hidden" name="token" value={token} /><button type="submit" className="month-apply">이용 안내 확인하고 초대 계속</button></form></div>
      : invitation.account_exists && !isMatchingAccount ? <div className="workspace-form"><p>초대한 이메일의 기존 계정으로 로그인하면 이 초대를 수락할 수 있습니다.</p>{current?.ok && <><p role="status">현재 로그인: {current.data.user_account.email}</p><LogoutButton returnTo={`/organization-invitations/${token}`} /></>}<form action={signInForInvitation}><input type="hidden" name="token" value={token} /><button type="submit" className="month-apply">해당 계정으로 로그인</button></form></div>
      : <>{!invitation.account_exists && current?.ok && <div className="workspace-form"><p role="status">현재 {current.data.user_account.email} 계정으로 로그인되어 있습니다. 이 초대는 {invitation.email} 새 계정용이며, 수락하면 새 계정으로 로그인이 바뀝니다. 다른 사람에게 보낼 링크라면 수락하지 말고 링크만 전달하세요.</p><LogoutButton returnTo={`/organization-invitations/${token}`} /></div>}<InvitationForm token={token} accountExists={invitation.account_exists} /></>}
    {current?.ok && <div className="access-actions"><Link href="/organizations">도입사 선택으로</Link></div>}
  </AuthFrame>;
}
