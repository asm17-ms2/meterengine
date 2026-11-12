"use client";

import { Alert, Button, Input } from "antd";
import { startTransition, useActionState, useEffect, useRef } from "react";
import { resendEmailVerificationAction } from "@/app/(public)/email-verifications/actions";
import { TurnstileWidget } from "@/components/auth/TurnstileWidget";
import type { ActionState } from "@/lib/auth/types";

export function EmailVerificationResendForm({ turnstileSiteKey }: { turnstileSiteKey: string }) {
  const [state, action, isPending] = useActionState<ActionState, FormData>(resendEmailVerificationAction, {});
  const feedbackRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (state.message) feedbackRef.current?.focus(); }, [state]);
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="auth-form" aria-busy={isPending}>
    {state.message && <div ref={feedbackRef} tabIndex={-1}><Alert type={state.done ? "success" : "error"} showIcon title={<span role={state.done ? "status" : "alert"}>{state.message}</span>} /></div>}
    <div className="form-field"><label htmlFor="email">이메일</label><Input id="email" name="email" type="email" autoComplete="email" required maxLength={254} size="large" /></div>
    {turnstileSiteKey && <TurnstileWidget siteKey={turnstileSiteKey} action="email_verification" resetKey={state} />}
    <Button htmlType="submit" size="large" block loading={isPending}>인증 메일 다시 받기</Button>
  </form>;
}
