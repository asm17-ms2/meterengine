import type { Metadata } from "next";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { AuthForm } from "@/components/auth/AuthForm";

export const metadata: Metadata = { title: "로그인" };

export default async function LoginPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const query = await searchParams;
  return <AuthFrame variant="login" currentStep={1} title="로그인" description="이메일과 비밀번호로 로그인하세요."><AuthForm mode="login" returnTo={typeof query.returnTo === "string" ? query.returnTo : ""} expired={query.reason === "expired"} signedUp={query.reason === "signed_up"} logoutUnconfirmed={query.reason === "logout_unconfirmed"} /></AuthFrame>;
}
