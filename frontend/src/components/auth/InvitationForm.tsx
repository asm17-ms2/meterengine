"use client";

import { startTransition, useActionState, useEffect, useRef } from "react";
import { Alert, Button } from "antd";
import { acceptInvitationAction } from "@/app/organization-invitations/[token]/actions";
import { CONSENT_DOCUMENTS } from "@/lib/auth/consents";

export function InvitationForm({ token, accountExists }: { token: string; accountExists: boolean }) {
  const [state, action, isPending] = useActionState(acceptInvitationAction, {});
  const feedbackRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (state.message) feedbackRef.current?.focus(); }, [state]);
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="workspace-form" aria-busy={isPending}>
    <input type="hidden" name="token" value={token} />
    {state.message && <div ref={feedbackRef} tabIndex={-1}><Alert showIcon type={state.isAccepted ? "success" : "error"} title={<span role={state.isAccepted ? "status" : "alert"}>{state.message}</span>} /></div>}
    <fieldset disabled={isPending || state.isAccepted}>
      {!accountExists && <><label className="workspace-field"><span>이름 (필수)</span><input name="name" autoComplete="name" required maxLength={255} /></label>
        <label className="workspace-field"><span>비밀번호 (필수)</span><input name="password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required /><small className="field-hint">8자 이상, UTF-8 기준 72바이트 이하.</small></label>
        <fieldset><legend>필수 동의</legend><p className="field-hint">현재 안내는 개발 검증용입니다. 정식 서비스 약관은 별도 확정이 필요합니다.</p>{CONSENT_DOCUMENTS.map(document => <div className="consent-item" key={document.document}><details><summary>{document.title} 전문 보기</summary><p>{document.text}</p></details><label><input type="checkbox" name={document.document} required />{document.title}를 읽고 동의합니다 (필수)</label></div>)}</fieldset></>}
      <Button type="primary" htmlType="submit" loading={isPending} disabled={state.isAccepted}>초대 수락하고 도입사로 이동</Button>
    </fieldset>
  </form>;
}
