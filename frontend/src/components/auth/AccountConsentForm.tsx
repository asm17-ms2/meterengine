"use client";

import { startTransition, useActionState, useEffect, useRef } from "react";
import { Alert, Button } from "antd";
import { acceptAccountConsents } from "@/app/account/consents/actions";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "@/lib/auth/consents";

export function AccountConsentForm({ returnTo, isOperator = false }: { returnTo: string; isOperator?: boolean }) {
  const [state, action, isPending] = useActionState(acceptAccountConsents, {});
  const feedbackRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (state.message) feedbackRef.current?.focus(); }, [state]);
  return <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }} className="workspace-form" aria-busy={isPending}><input type="hidden" name="returnTo" value={returnTo} />
    <p>{isOperator ? "기존 플랫폼 운영자가 만든 계정입니다. 안내를 읽고 동의한 뒤 플랫폼 운영을 시작하세요." : "대표관리자가 만든 계정입니다. 업무를 시작하기 전에 본인이 안내를 읽고 동의해 주세요."}</p>
    {state.message && <div ref={feedbackRef} tabIndex={-1}><Alert showIcon type="error" title={<span role="alert">{state.message}</span>} /></div>}
    <fieldset className="consent-fields" disabled={isPending}><legend>필수 동의</legend><p className="field-hint">개발 검증용 안내, {CONSENT_VERSION}. 정식 서비스 약관은 별도 확정이 필요합니다.</p>{CONSENT_DOCUMENTS.map(doc => <div key={doc.document}><details><summary>{doc.title} 전문 보기</summary><p>{doc.text}</p></details><label className="consent-check"><input type="checkbox" required name={doc.document} />{doc.title}에 동의합니다 (필수)</label></div>)}</fieldset>
    <Button type="primary" htmlType="submit" loading={isPending}>{isOperator ? "동의하고 운영 시작" : "동의하고 업무 시작"}</Button>
  </form>;
}
