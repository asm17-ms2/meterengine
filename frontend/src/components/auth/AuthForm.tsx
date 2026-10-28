"use client";

import { Alert, Button, Input } from "antd";
import Link from "next/link";
import { startTransition, useActionState, useEffect, useRef } from "react";
import { loginAction, signupAction } from "@/app/(public)/actions";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "@/lib/auth/consents";
import type { ActionState } from "@/lib/auth/types";

export function AuthForm({ mode, returnTo = "", expired = false }: { mode: "login" | "signup"; returnTo?: string; expired?: boolean }) {
  const [state, action, isPending] = useActionState<ActionState, FormData>(mode === "login" ? loginAction : signupAction, {});
  const errorRef = useRef<HTMLDivElement>(null);
  const isSignup = mode === "signup";
  useEffect(() => { if (state.message) errorRef.current?.focus(); }, [state]);
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="auth-form" aria-busy={isPending}>
    {expired && <Alert type="info" showIcon title="로그인이 만료되었거나 종료되었습니다. 다시 로그인하세요." />}
    {state.message && <div ref={errorRef} tabIndex={-1}><Alert type="error" showIcon title={state.message} /></div>}
    <input type="hidden" name="returnTo" value={returnTo} />
    {isSignup && <><div className="form-field"><label htmlFor="organization_name">도입사 이름</label><Input id="organization_name" name="organization_name" autoComplete="organization" required maxLength={255} size="large" /></div><div className="form-field"><label htmlFor="name">담당자 이름</label><Input id="name" name="name" autoComplete="name" required maxLength={255} size="large" /></div></>}
    <div className="form-field"><label htmlFor="email">이메일</label><Input id="email" name="email" type="email" autoComplete="username" required maxLength={254} size="large" /></div>
    <div className="form-field"><label htmlFor="password">비밀번호</label><Input.Password id="password" name="password" autoComplete={isSignup ? "new-password" : "current-password"} required minLength={isSignup ? 8 : undefined} maxLength={isSignup ? 72 : undefined} size="large" aria-describedby={isSignup ? "password-help" : undefined} />{isSignup && <span id="password-help" className="field-hint">8자 이상 72자 이하. 한글은 1자를 3자로 계산합니다</span>}</div>
    {isSignup && <fieldset className="consent-fields"><legend>필수 동의</legend><p className="field-hint">개발 검증용 안내, {CONSENT_VERSION}. 정식 서비스 약관은 별도 확정이 필요합니다.</p>{CONSENT_DOCUMENTS.map(doc => <div key={doc.document}><details><summary>{doc.title} 전문 보기</summary><p>{doc.text}</p></details><label className="consent-check"><input type="checkbox" required name={doc.document} />{doc.title}에 동의합니다 (필수)</label></div>)}</fieldset>}
    <Button type="primary" htmlType="submit" size="large" block loading={isPending}>{isSignup ? "가입 신청" : "로그인"}</Button>
    <p className="auth-switch">{isSignup ? "이미 계정이 있나요?" : "처음 이용하나요?"} <Link href={isSignup ? "/login" : "/signup"}>{isSignup ? "로그인" : "가입 신청"}</Link></p>
  </form>;
}
