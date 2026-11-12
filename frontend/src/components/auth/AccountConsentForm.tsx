"use client";

import { startTransition, useActionState, useEffect, useRef } from "react";
import { Alert, Button } from "antd";
import { acceptAccountConsents } from "@/app/account/consents/actions";
import { ConsentFields } from "@/components/auth/ConsentFields";

export function AccountConsentForm({ returnTo, isOperator = false }: { returnTo: string; isOperator?: boolean }) {
  const [state, action, isPending] = useActionState(acceptAccountConsents, {});
  const feedbackRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (state.message) feedbackRef.current?.focus(); }, [state]);
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="workspace-form" aria-busy={isPending}><input type="hidden" name="returnTo" value={returnTo} />
    <p>{isOperator ? "다른 플랫폼 운영자가 만든 계정이에요. 처음 받은 비밀번호 대신 쓸 새 비밀번호를 정하고, 약관과 개인정보 처리방침을 읽고 동의한 뒤 플랫폼 운영을 시작하세요." : "대표관리자가 만든 계정이에요. 업무를 시작하기 전에 처음 받은 비밀번호 대신 쓸 새 비밀번호를 정하고, 약관과 개인정보 처리방침을 직접 읽고 동의하세요."}</p>
    {state.message && <div ref={feedbackRef} tabIndex={-1}><Alert showIcon type="error" title={<span role="alert">{state.message}</span>} /></div>}
    <fieldset disabled={isPending}><label className="workspace-field"><span>새 비밀번호 (필수)</span><input name="newPassword" type="password" autoComplete="new-password" minLength={8} maxLength={72} required /><small className="field-hint">8자 이상 72자 이하예요. 한글은 1자를 3자로 계산해요. 처음 받은 비밀번호는 더 쓸 수 없고, 다른 브라우저나 기기에서 로그인한 이 계정도 로그아웃돼요.</small></label>
      <label className="workspace-field"><span>새 비밀번호 확인 (필수)</span><input name="newPasswordConfirm" type="password" autoComplete="new-password" minLength={8} maxLength={72} required /></label></fieldset>
    <ConsentFields disabled={isPending} />
    <Button type="primary" htmlType="submit" loading={isPending}>{isOperator ? "동의하고 운영 시작" : "동의하고 업무 시작"}</Button>
  </form>;
}
