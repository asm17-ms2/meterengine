"use client";

import { Alert, Button } from "antd";
import Link from "next/link";
import { startTransition, useActionState, useEffect, useRef } from "react";
import { confirmEmailVerificationAction, type ConfirmEmailVerificationState } from "@/app/(public)/email-verifications/actions";

export function EmailVerificationConfirmForm({ token }: { token: string }) {
  const [state, action, isPending] = useActionState<ConfirmEmailVerificationState, FormData>(confirmEmailVerificationAction, {});
  const feedbackRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (state.message) feedbackRef.current?.focus(); }, [state]);
  if (state.done) return <div className="auth-form"><div ref={feedbackRef} tabIndex={-1}><Alert type="success" showIcon title={<span role="status">{state.message}</span>} /></div><Link className="primary-link" href="/pending">다음 단계로</Link></div>;
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="auth-form" aria-busy={isPending}>
    {state.message && <div ref={feedbackRef} tabIndex={-1}><Alert type="error" showIcon title={<span role="alert">{state.message}</span>} /></div>}
    <input type="hidden" name="token" value={token} />
    <Button type="primary" htmlType="submit" size="large" block loading={isPending}>이메일 인증</Button>
    {state.isLinkUnusable && <>
      <p className="auth-switch">인증을 마쳤나요? <Link href="/login">로그인</Link></p>
      <p className="auth-switch">아직 인증하지 않았나요? <Link href="/email-verifications">인증 메일 다시 받기</Link></p>
    </>}
  </form>;
}
