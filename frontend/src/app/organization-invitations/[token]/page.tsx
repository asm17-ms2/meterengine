import Link from "next/link";
import type { Metadata } from "next";
import { Button } from "antd";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { InvitationForm } from "@/components/auth/InvitationForm";
import { LogoutButton } from "@/components/auth/SessionControls";
import { requestApi } from "@/lib/api/transport";
import { readSessionToken } from "@/lib/auth/session";
import { formatKstDateTime } from "@/lib/format";
import { scopeSummary } from "@/lib/auth/permissions";
import type { SessionResponse } from "@/lib/auth/types";
import { reviewConsentsForInvitation, signInForInvitation } from "./actions";

export const metadata: Metadata = { title: "초대 수락", robots: { index: false, follow: false }, referrer: "no-referrer" };

type OrganizationInvitationPreviewResponse = { organization_name: string; email: string; expires_at: string; account_exists: boolean; scopes: string[] };

export default async function OrganizationInvitationPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!/^me_invite_[A-Za-z0-9]{32}$/.test(token)) return <AuthFrame variant="join" title="초대 링크 확인" description="초대 링크가 올바르지 않아요."><Link href="/login">로그인으로</Link></AuthFrame>;
  const result = await requestApi<OrganizationInvitationPreviewResponse>(`/v1/organization-invitations/${encodeURIComponent(token)}`);
  if (!result.ok) return <AuthFrame variant="join" title="초대 링크 확인" description={result.error.message}><p>만료되거나 취소된 링크라면 대표관리자에게 새 초대를 요청하세요.</p><Link href="/login">로그인으로</Link></AuthFrame>;
  const invitation = result.data;
  const sessionToken = await readSessionToken();
  const current = sessionToken ? await requestApi<SessionResponse>("/v1/sessions/current", { token: sessionToken }) : null;
  const isMatchingAccount = current?.ok && current.data.user_account.email.toLowerCase() === invitation.email.toLowerCase();
  return <AuthFrame variant="join" currentStep={1} title={`${invitation.organization_name}에 참여`} description={`${invitation.email} 주소로 초대를 받았어요.`}>
    <p>업무 권한: {scopeSummary(invitation.scopes)}</p>
    <p className="field-hint">초대는 {formatKstDateTime(invitation.expires_at)} KST까지 한 번 수락할 수 있어요.</p>
    {isMatchingAccount && current?.ok && current.data.consent_required ? <div className="workspace-form"><p>이 계정은 약관과 개인정보 처리방침에 먼저 동의해야 해요. 동의를 마치면 초대 수락으로 돌아와요.</p><form action={reviewConsentsForInvitation}><input type="hidden" name="token" value={token} /><Button type="primary" htmlType="submit">약관에 동의하고 초대 계속</Button></form></div>
      : invitation.account_exists && !isMatchingAccount ? <div className="workspace-form"><p>{invitation.email} 계정으로 로그인하면 이 초대를 수락할 수 있어요.</p>{current?.ok && <><p role="status">현재 로그인: {current.data.user_account.email}</p><LogoutButton returnTo={`/organization-invitations/${token}`} /></>}<form action={signInForInvitation}><input type="hidden" name="token" value={token} /><Button type="primary" htmlType="submit">초대받은 계정으로 로그인</Button></form></div>
      : <>{!invitation.account_exists && current?.ok && <div className="workspace-form"><p role="status">현재 {current.data.user_account.email} 계정으로 로그인돼 있어요. 이 초대를 수락하면 {invitation.email} 계정을 새로 만들고 그 계정으로 로그인해요. 다른 사람이 받을 초대라면 수락하지 말고 링크를 전달하세요.</p><LogoutButton returnTo={`/organization-invitations/${token}`} /></div>}<InvitationForm token={token} accountExists={invitation.account_exists} /></>}
    {current?.ok && <div className="access-actions"><Link href="/organizations">조직 선택으로</Link></div>}
  </AuthFrame>;
}
