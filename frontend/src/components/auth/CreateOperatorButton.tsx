"use client";

import { Alert, App, Button, Modal } from "antd";
import { startTransition, useActionState, useEffect, useId, useRef, useState, type FormEvent } from "react";
import { createOperatorAction, type CreateOperatorField, type CreateOperatorState } from "@/app/admin/actions";

const FIELDS = [
  { name: "name", label: "이름", type: "text", autoComplete: "name", maxLength: 255 },
  { name: "email", label: "이메일", type: "email", autoComplete: "email", maxLength: 254 },
  { name: "password", label: "초기 비밀번호", type: "password", autoComplete: "new-password", maxLength: 72 },
] as const;

function CreateOperatorDialog({ onClose }: { onClose: () => void }) {
  const { message } = App.useApp();
  const formId = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const feedbackRef = useRef<HTMLDivElement>(null);
  const [isDirty, setIsDirty] = useState(false);
  const [dismissedErrors, setDismissedErrors] = useState<{ state: CreateOperatorState; names: string[] }>({ state: {}, names: [] });
  const [state, action, isPending] = useActionState<CreateOperatorState, FormData>(async (previous, data) => {
    const result = await createOperatorAction(previous, data);
    if (result.isCreated) {
      setIsDirty(false);
      message.success(result.message ?? "운영자 계정을 만들었습니다.");
      onClose();
    }
    if (result.isUnconfirmed) setIsDirty(false);
    return result;
  }, {});
  const errors = Object.fromEntries(Object.entries(state.fieldErrors ?? {}).filter(([name]) => dismissedErrors.state !== state || !dismissedErrors.names.includes(name))) as Partial<Record<CreateOperatorField, string>>;
  const hasFeedback = Boolean(state.message && (!state.fieldErrors || Object.keys(errors).length));

  useEffect(() => {
    if (!state.message || state.isCreated) return;
    const fields = Object.keys(state.fieldErrors ?? {});
    if (fields.length === 1) formRef.current?.querySelector<HTMLElement>(`[name="${CSS.escape(fields[0])}"]`)?.focus();
    else feedbackRef.current?.focus();
  }, [state]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    startTransition(() => action(data));
  }

  function close() {
    if (isPending) return;
    if (isDirty && !window.confirm("작성 중인 내용을 버리고 닫을까요?")) return;
    onClose();
  }

  return <Modal title="플랫폼 운영자 계정 생성" open className="workspace-dialog" width="min(600px, calc(100vw - 32px))" style={{ top: 24 }} styles={{ body: { maxHeight: "calc(100dvh - 210px)", overflowY: "auto", padding: "4px 4px 4px 0" } }}
    afterOpenChange={isOpen => { if (isOpen) formRef.current?.querySelector<HTMLInputElement>('input[name="name"]')?.focus(); }}
    footer={<div className="workspace-dialog-actions"><Button onClick={close} disabled={isPending}>닫기</Button><Button form={formId} type="primary" htmlType="submit" loading={isPending} disabled={isPending || state.isUnconfirmed}>운영자 계정 생성</Button></div>}
    onCancel={close} keyboard={!isPending} mask={{ closable: !isPending }} closable={{ disabled: isPending }}>
    <form id={formId} ref={formRef} onSubmit={submit} onChange={event => {
      setIsDirty(true);
      const target = event.nativeEvent.target;
      const name = target instanceof HTMLInputElement ? target.name : "";
      setDismissedErrors(current => ({ state, names: current.state === state ? [...current.names, name] : [name] }));
    }} className="workspace-form" aria-busy={isPending}>
      <p className="muted">MeterEngine의 가입 승인과 플랫폼 권한을 관리할 계정을 만듭니다. 도입사 소속은 추가하지 않습니다.</p>
      <p className="field-hint">새 운영자는 처음 로그인해 이용 안내에 동의한 뒤 운영을 시작합니다. 초기 로그인 정보는 본인에게 안전하게 전달하세요.</p>
      {hasFeedback && <div ref={feedbackRef} tabIndex={-1} className="form-feedback"><Alert showIcon type={state.isUnconfirmed ? "warning" : "error"} title={state.message}
        description={Object.keys(errors).length ? <ul>{Object.entries(errors).map(([name, error]) => <li key={name}><a href={`#${formId}-${name}`} onClick={event => { event.preventDefault(); document.getElementById(`${formId}-${name}`)?.focus(); }}>{FIELDS.find(field => field.name === name)?.label}: {error}</a></li>)}</ul> : undefined} /></div>}
      <fieldset disabled={isPending || state.isUnconfirmed}>
        {FIELDS.map(field => {
          const id = `${formId}-${field.name}`;
          const error = errors[field.name];
          const errorId = error ? `${id}-error` : undefined;
          const hintId = field.name === "password" ? `${id}-hint` : undefined;
          return <div className="workspace-field" key={field.name}>
            <label htmlFor={id}>{field.label}<span className="required-label"> (필수)</span></label>
            <input id={id} name={field.name} type={field.type} autoComplete={field.autoComplete} maxLength={field.maxLength} minLength={field.name === "password" ? 8 : undefined} required aria-invalid={Boolean(error)} aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined} spellCheck={field.name === "email" ? false : undefined} />
            {hintId && <small className="field-hint" id={hintId}>8자 이상, UTF-8 기준 72바이트 이하.</small>}
            {error && <small className="field-error" id={errorId}>{error}</small>}
          </div>;
        })}
      </fieldset>
      {state.isUnconfirmed && <a href="/admin/users">사용자 목록 다시 조회</a>}
    </form>
  </Modal>;
}

export function CreateOperatorButton() {
  const triggerId = useId();
  const [isOpen, setIsOpen] = useState(false);
  function close() {
    setIsOpen(false);
    requestAnimationFrame(() => document.getElementById(triggerId)?.focus());
  }
  return <><Button id={triggerId} type="primary" onClick={() => setIsOpen(true)}>운영자 계정 생성</Button>{isOpen && <CreateOperatorDialog onClose={close} />}</>;
}
