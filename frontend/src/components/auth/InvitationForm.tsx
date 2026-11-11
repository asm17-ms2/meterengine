"use client";

import { startTransition, useActionState, useEffect, useRef } from "react";
import { Alert, Button } from "antd";
import { acceptInvitationAction } from "@/app/organization-invitations/[token]/actions";
import { ConsentFields } from "@/components/auth/ConsentFields";

export function InvitationForm({ token, accountExists }: { token: string; accountExists: boolean }) {
  const [state, action, isPending] = useActionState(acceptInvitationAction, {});
  const feedbackRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (state.message) feedbackRef.current?.focus(); }, [state]);
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="workspace-form" aria-busy={isPending}>
    <input type="hidden" name="token" value={token} />
    {state.message && <div ref={feedbackRef} tabIndex={-1}><Alert showIcon type={state.isAccepted ? "success" : "error"} title={<span role={state.isAccepted ? "status" : "alert"}>{state.message}</span>} /></div>}
    <fieldset disabled={isPending || state.isAccepted}>
      {!accountExists && <><label className="workspace-field"><span>이름 (필수)</span><input name="name" autoComplete="name" required maxLength={255} /></label>
        <label className="workspace-field"><span>비밀번호 (필수)</span><input name="password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required /><small className="field-hint">8자 이상 72자 이하. 한글은 1자를 3자로 계산합니다.</small></label>
        <ConsentFields /></>}
      <Button type="primary" htmlType="submit" loading={isPending} disabled={state.isAccepted}>초대 수락하고 도입사로 이동</Button>
    </fieldset>
  </form>;
}
