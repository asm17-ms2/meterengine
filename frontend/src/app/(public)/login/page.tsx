import type { Metadata } from "next";
import { AuthFrame } from "@/components/auth/AuthFrame";
import { AuthForm } from "@/components/auth/AuthForm";

export const metadata: Metadata = { title: "로그인" };

export default async function LoginPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const query = await searchParams;
  return <AuthFrame variant="login" currentStep={1} title="로그인" description="계정으로 로그인해 도입사 업무 또는 플랫폼 운영을 시작하세요."><AuthForm mode="login" returnTo={typeof query.returnTo === "string" ? query.returnTo : ""} expired={query.reason === "expired"} /></AuthFrame>;
}
