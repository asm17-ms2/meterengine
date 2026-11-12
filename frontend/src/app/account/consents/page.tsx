import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { AccountConsentForm } from "@/components/auth/AccountConsentForm";
import { LogoutButton } from "@/components/auth/SessionControls";
import { requireSession } from "@/lib/auth/session";
import { safeReturnPath } from "@/lib/auth/navigation";
import { workspaceHref } from "@/lib/workspace-href";

export const metadata: Metadata = { title: "약관 동의" };

export default async function AccountConsentsPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const session = await requireSession();
  const isOperator = session.user_account.role === "operator";
  const query = await searchParams;
  const returnTo = workspaceHref(safeReturnPath(query.returnTo, isOperator), session.organization ? new URLSearchParams({ organization_id: session.organization.id }) : "");
  if (!session.consent_required) redirect(returnTo);
  return <AuthFrame variant={isOperator ? "operator" : "join"} currentStep={2} title="약관 동의" description={`${session.user_account.name} (${session.user_account.email}) 계정으로 시작하려면 이용약관과 개인정보 처리방침을 확인하세요.`}><AccountConsentForm returnTo={returnTo} isOperator={isOperator} /><div className="access-actions"><LogoutButton /></div></AuthFrame>;
}
