"use client";

import { Alert, Button, Input } from "antd";
import Link from "next/link";
import { startTransition, useActionState, useEffect, useRef } from "react";
import { loginAction, signupAction } from "@/app/(public)/actions";
import { ConsentFields } from "@/components/auth/ConsentFields";
import type { ActionState } from "@/lib/auth/types";
import { TurnstileWidget } from "@/components/auth/TurnstileWidget";

export function AuthForm({ mode, returnTo = "", expired = false, signedUp = false, logoutUnconfirmed = false, turnstileSiteKey = "" }: { mode: "login" | "signup"; returnTo?: string; expired?: boolean; signedUp?: boolean; logoutUnconfirmed?: boolean; turnstileSiteKey?: string }) {
  const [state, action, isPending] = useActionState<ActionState, FormData>(mode === "login" ? loginAction : signupAction, {});
  const errorRef = useRef<HTMLDivElement>(null);
  const isSignup = mode === "signup";
  useEffect(() => { if (state.message) errorRef.current?.focus(); }, [state]);
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="auth-form" aria-busy={isPending}>
    {expired && <Alert type="info" showIcon title="로그인이 만료됐거나 끝났어요. 다시 로그인하세요." />}
    {signedUp && <Alert type="success" showIcon title="가입했어요. 가입한 이메일과 비밀번호로 로그인하세요." />}
    {logoutUnconfirmed && <Alert type="warning" showIcon title="이 브라우저에서 로그아웃했어요. 서버에서 로그아웃을 확인하지 못해, 이 로그인은 만료 시각까지 남아 있을 수 있어요." />}
    {state.message && <div ref={errorRef} tabIndex={-1}><Alert type="error" showIcon title={state.message} /></div>}
    <input type="hidden" name="returnTo" value={returnTo} />
    {isSignup && <><div className="form-field"><label htmlFor="organization_name">조직 이름</label><Input id="organization_name" name="organization_name" autoComplete="organization" required maxLength={255} size="large" /></div><div className="form-field"><label htmlFor="name">담당자 이름</label><Input id="name" name="name" autoComplete="name" required maxLength={255} size="large" /></div></>}
    <div className="form-field"><label htmlFor="email">이메일</label><Input id="email" name="email" type="email" autoComplete="username" required maxLength={254} size="large" /></div>
    <div className="form-field"><label htmlFor="password">비밀번호</label><Input.Password id="password" name="password" autoComplete={isSignup ? "new-password" : "current-password"} required minLength={isSignup ? 8 : undefined} maxLength={isSignup ? 72 : undefined} size="large" aria-describedby={isSignup ? "password-help" : undefined} />{isSignup && <span id="password-help" className="field-hint">8자 이상 72자 이하예요. 한글은 1자를 3자로 계산해요.</span>}</div>
    {isSignup && <ConsentFields />}
    {isSignup && turnstileSiteKey && <TurnstileWidget siteKey={turnstileSiteKey} action="signup" resetKey={state} />}
    <Button type="primary" htmlType="submit" size="large" block loading={isPending}>{isSignup ? "가입" : "로그인"}</Button>
    <p className="auth-switch">{isSignup ? "이미 계정이 있나요?" : "처음 이용하나요?"} <Link href={isSignup ? "/login" : "/signup"}>{isSignup ? "로그인" : "가입"}</Link></p>
  </form>;
}
