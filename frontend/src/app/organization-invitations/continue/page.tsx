import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import Link from "next/link";
import type { Metadata } from "next";
import { AuthFrame } from "@/components/auth/AuthFrame";

export const metadata: Metadata = { robots: { index: false, follow: false }, referrer: "no-referrer" };

export default async function ContinueInvitationPage() {
  const token = (await cookies()).get("me_invitation_flow")?.value;
  if (token && /^me_invite_[A-Za-z0-9]{32}$/.test(token)) redirect(`/organization-invitations/${encodeURIComponent(token)}`);
  return <AuthFrame variant="join" title="초대 링크를 다시 열어 주세요" description="로그인 연결의 유효 시간이 지났거나 초대 정보를 확인하지 못했습니다."><p>대표관리자에게 받은 원래 링크를 다시 열면 초대 수락을 이어갈 수 있습니다.</p><Link href="/overview">콘솔로 이동</Link></AuthFrame>;
}
