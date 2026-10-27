import { AuthFrame } from "@/components/auth/AuthFrame";
import { AuthForm } from "@/components/auth/AuthForm";

export default async function LoginPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const query = await searchParams;
  return <AuthFrame variant="login" title="로그인" description="계정으로 로그인해 도입사 업무 또는 플랫폼 운영을 시작하세요."><AuthForm mode="login" returnTo={typeof query.returnTo === "string" ? query.returnTo : ""} expired={query.reason === "expired"} /></AuthFrame>;
}
