import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import Link from "next/link";
import type { Metadata } from "next";
import { AuthFrame } from "@/components/auth/AuthFrame";

export const metadata: Metadata = { title: "초대 수락", robots: { index: false, follow: false }, referrer: "no-referrer" };

export default async function ContinueInvitationPage() {
  const token = (await cookies()).get("me_invitation_flow")?.value;
  if (token && /^me_invite_[A-Za-z0-9]{32}$/.test(token)) redirect(`/organization-invitations/${encodeURIComponent(token)}`);
  return <AuthFrame variant="join" title="초대 링크를 다시 여세요" description="초대 정보를 확인하지 못했어요. 초대 링크를 연 지 30분이 지났을 수 있어요."><p>대표관리자에게 받은 원래 링크를 다시 열면 초대 수락을 이어 갈 수 있어요.</p><Link href="/overview">콘솔로 이동</Link></AuthFrame>;
}
