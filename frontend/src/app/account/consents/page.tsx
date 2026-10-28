import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { AccountConsentForm } from "@/components/auth/AccountConsentForm";
import { LogoutButton } from "@/components/auth/SessionControls";
import { requireSession } from "@/lib/auth/session";
import { safeReturnPath } from "@/lib/auth/navigation";
import { workspaceHref } from "@/lib/workspace-href";

export const metadata: Metadata = { title: "계정 이용 안내 확인" };

export default async function AccountConsentsPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const session = await requireSession();
  const isOperator = session.user_account.role === "operator";
  const query = await searchParams;
  const returnTo = workspaceHref(safeReturnPath(query.returnTo, isOperator), session.organization ? new URLSearchParams({ organization_id: session.organization.id }) : "");
  if (!session.consent_required) redirect(returnTo);
  return <AuthFrame variant={isOperator ? "operator" : "join"} title="계정 이용 안내 확인" description={`${session.user_account.name} (${session.user_account.email}) 계정의 이용 안내를 확인하세요.`}><AccountConsentForm returnTo={returnTo} isOperator={isOperator} /><LogoutButton /></AuthFrame>;
}
