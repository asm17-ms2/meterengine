"use client";


import { Alert, App, Button, Checkbox, Modal } from "antd";
import { startTransition, useActionState, useEffect, useId, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { unstable_rethrow } from "next/navigation";

import { workspaceAction, type WorkspaceActionState } from "@/app/(console)/workspace-actions";
import { useTenantAccess } from "@/components/auth/TenantAccess";
import { canPerformWorkspaceOperation } from "@/lib/auth/permissions";

export type WorkspaceField = {
  name: string;
  label: string;
  type?: "text" | "email" | "password" | "number" | "month" | "url" | "textarea" | "select" | "hidden";
  value?: string;
  placeholder?: string;
  hint?: string;
  isOptional?: boolean;
  options?: { value: string; label: string }[];
  isMultiple?: boolean;
  min?: number;
  max?: number;
  step?: number | "any";
  maxLength?: number;
  autoComplete?: string;
};

export type ActionPanelProps = { title: string; operation: string; description?: string; fields?: WorkspaceField[]; label?: string; isDanger?: boolean; isInline?: boolean };

function ActionBody({ title, operation, description, fields = [], label, isDanger = false, isInline = false, onSuccess, requestId, onClose, onNewRequest }: ActionPanelProps & { onSuccess?: () => void; requestId?: string; onClose?: () => void; onNewRequest?: () => void }) {
  const { message } = App.useApp();
  const tenantAccess = useTenantAccess();
  const [mutationScope] = useState(() => ({ organizationId: tenantAccess?.organizationId ?? "" }));
  const formId = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const summaryRef = useRef<HTMLDivElement>(null);
  const resultRef = useRef<HTMLDivElement>(null);
  const [isDirty, setIsDirty] = useState(false);
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({});
  const [copyResult, setCopyResult] = useState("");
  const [isCopied, setIsCopied] = useState(false);
  const [dismissedErrors, setDismissedErrors] = useState<{ state: WorkspaceActionState; names: string[] }>({ state: {}, names: [] });
  const [state, action, isPending] = useActionState<WorkspaceActionState, FormData>(async (previous, data) => {
    let result: WorkspaceActionState;
    try {
      result = await workspaceAction(previous, data);
    } catch (error) {
      unstable_rethrow(error);
      return { message: "요청 결과를 확인하지 못했습니다. 목록을 새로 조회해 반영됐는지 확인한 뒤 다시 시도하세요." };
    }
    if (result.isSuccess) {
      setIsDirty(false);
      if (!result.secret && !result.registrationPath && !isInline) {
        message.success(result.message ?? "저장했습니다.");
        onSuccess?.();
      }
    }
    return result;
  }, {});
  const errors = { ...Object.fromEntries(Object.entries(state.fieldErrors ?? {}).filter(([name]) => dismissedErrors.state !== state || !dismissedErrors.names.includes(name))), ...clientErrors };
  const visibleMessage = state.fieldErrors && Object.keys(state.fieldErrors).length > 0 && Object.keys(errors).length === 0 ? undefined : state.message;

  useEffect(() => {
    if (state.secret || state.registrationPath) { resultRef.current?.focus(); return; }
    if (!state.message || state.isSuccess) return;
    const invalid = Object.keys(state.fieldErrors ?? {});
    if (invalid.length === 1) formRef.current?.querySelector<HTMLElement>(`[data-field="${CSS.escape(invalid[0])}"]`)?.focus();
    else summaryRef.current?.focus();
  }, [state]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const nextErrors: Record<string, string> = {};
    for (const field of fields) {
      if (field.isMultiple && !field.isOptional && data.getAll(field.name).length === 0) nextErrors[field.name] = `${field.label}을 하나 이상 선택하세요.`;
    }
    setClientErrors(nextErrors);
    if (Object.keys(nextErrors).length) { requestAnimationFrame(() => summaryRef.current?.focus()); return; }
    startTransition(() => action(data));
  }

  function close() {
    if (isPending) return;
    if (isDirty && !window.confirm("작성 중인 내용을 버리고 닫을까요?")) return;
    if ((state.secret || state.registrationPath) && !isCopied && !window.confirm("닫으면 이 값을 다시 볼 수 없습니다. 복사하지 않고 닫을까요?")) return;
    onClose?.();
  }

  async function copy(value: string) {
    setCopyResult("");
    try { await navigator.clipboard.writeText(value); requestAnimationFrame(() => setCopyResult("복사했습니다.")); setIsCopied(true); }
    catch { requestAnimationFrame(() => setCopyResult("복사하지 못했습니다. 표시된 값을 선택해 직접 복사하세요.")); }
  }

  const isLocked = Boolean(state.secret || state.registrationPath || state.isUnconfirmed || state.isConfirmedFailure);
  const submitButton = <Button form={formId} type={isDanger ? "default" : "primary"} danger={isDanger} htmlType="submit" loading={isPending} disabled={isLocked}>{label ?? title}</Button>;
  const form = <form id={formId} ref={formRef} onSubmit={submit} onChange={event => { setIsDirty(true); const target = event.nativeEvent.target; const name = target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement ? target.name : ""; setClientErrors(current => Object.fromEntries(Object.entries(current).filter(([key]) => key !== name))); setDismissedErrors(current => ({ state, names: current.state === state ? [...current.names, name] : [name] })); }} className="workspace-form" aria-busy={isPending}>
    <input type="hidden" name="operation" value={operation} />
    <input type="hidden" name="organization_id" value={mutationScope.organizationId} />
    {requestId && <input type="hidden" name="request_id" value={requestId} />}
    {description && <p className="muted">{description}</p>}
    {(visibleMessage || Object.keys(clientErrors).length > 0) && <div ref={summaryRef} tabIndex={-1} className="form-feedback"><Alert type={state.isUnconfirmed ? "warning" : state.isSuccess ? "success" : "error"} showIcon title={visibleMessage ?? "입력한 내용을 확인하세요."} description={Object.keys(errors).length > 0 ? <ul>{Object.entries(errors).map(([name, error]) => <li key={name}><a href={`#${formId}-${name}`} onClick={event => { event.preventDefault(); document.getElementById(`${formId}-${name}`)?.focus(); }}>{fields.find(field => field.name === name)?.label ?? name}: {error}</a></li>)}</ul> : undefined} /></div>}
    {state.secret && <div ref={resultRef} tabIndex={-1} className="secret-result"><strong>이 창에서만 확인할 수 있습니다</strong><p>안전한 곳에 보관한 뒤 창을 닫으세요.</p><code>{state.secret}</code><Button onClick={() => copy(state.secret!)}>비밀값 복사</Button></div>}
    {state.registrationPath && <div ref={resultRef} tabIndex={-1} className="secret-result"><p>{state.registrationLabel ? "한 번 사용할 수 있는 초대 링크입니다. 닫기 전에 복사하고 초대할 사람에게 안전하게 전달하세요." : "24시간 동안 한 번 사용할 수 있는 카드 등록 링크입니다."}</p><Link href={state.registrationPath} target="_blank" rel="noreferrer">{state.registrationLabel ?? "카드 등록 링크"} 열기</Link><Button onClick={() => copy(new URL(state.registrationPath!, window.location.origin).href)}>{state.registrationLabel ?? "카드 등록 링크"} 복사</Button></div>}
    <div role="status">{copyResult && <p className="field-hint">{copyResult}</p>}</div>
    <fieldset disabled={isPending || isLocked}>
      {fields.map(field => {
        if (field.type === "hidden") return <input key={field.name} type="hidden" name={field.name} value={field.value ?? ""} />;
        const id = `${formId}-${field.name}`;
        const hintId = field.hint ? `${id}-hint` : undefined;
        const errorId = errors[field.name] ? `${id}-error` : undefined;
        const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
        const error = errors[field.name];
        if (field.isMultiple && field.options) return <fieldset key={field.name} className="workspace-choice-group" aria-describedby={describedBy} aria-invalid={Boolean(error)}>
          <legend>{field.label}{field.isOptional ? " (선택)" : " (필수)"}</legend>
          <div id={id} tabIndex={-1}><Checkbox.Group name={field.name} defaultValue={field.value?.split(",").filter(Boolean)} options={field.options} aria-label={field.label} /></div>
          {field.hint && <small className="field-hint" id={hintId}>{field.hint}</small>}
          {error && <small id={errorId} className="field-error">{error}</small>}
        </fieldset>;
        const common = { id, name: field.name, "data-field": field.name, "aria-invalid": Boolean(error), "aria-describedby": describedBy, required: !field.isOptional };
        return <div key={field.name} className="workspace-field">
          <label htmlFor={id}>{field.label}{field.isOptional ? <small> (선택)</small> : <span className="required-label"> (필수)</span>}</label>
          {field.options ? <select {...common} defaultValue={field.value}><option value="">선택하세요</option>{field.options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select>
            : field.type === "textarea" ? <textarea {...common} rows={4} defaultValue={field.value} placeholder={field.placeholder} maxLength={field.maxLength} />
              : <input {...common} type={field.type ?? "text"} defaultValue={field.type === "password" ? undefined : field.value} placeholder={field.placeholder} autoComplete={field.autoComplete ?? (field.type === "password" ? "new-password" : field.type === "email" ? "email" : "off")} spellCheck={field.type === "email" || field.type === "url" ? false : undefined} step={field.type === "number" ? field.step ?? 1 : undefined} min={field.type === "number" ? field.min ?? 0 : undefined} max={field.max} maxLength={field.maxLength} />}
          {field.hint && <small className="field-hint" id={hintId}>{field.hint}</small>}
          {error && <small id={errorId} className="field-error">{error}</small>}
        </div>;
      })}
    </fieldset>
    {state.isConfirmedFailure && onNewRequest && <Button onClick={onNewRequest}>{operation === "createPaymentCancellation" ? "새 취소 요청" : "새 요청"}</Button>}
    {state.isUnconfirmed && <a href="">결제와 취소 내역 새로 조회</a>}
    {isInline && submitButton}
  </form>;
  if (isInline) return form;
  return <Modal title={title} open className="workspace-dialog" width="min(600px, calc(100vw - 32px))" style={{ top: 24 }} styles={{ body: { maxHeight: "calc(100dvh - 210px)", overflowY: "auto", padding: "4px 4px 4px 0" } }} footer={<div className="workspace-dialog-actions"><Button onClick={close} disabled={isPending}>닫기</Button>{submitButton}</div>} onCancel={close} keyboard={!isPending} mask={{ closable: !isPending }} closable={{ disabled: isPending }}>{form}</Modal>;
}

export function ActionPanel(props: ActionPanelProps) {
  const access = useTenantAccess();
  const [isOpen, setIsOpen] = useState(false);
  const [requestId, setRequestId] = useState("");
  const triggerId = useId();
  const restoreFocus = () => requestAnimationFrame(() => document.getElementById(triggerId)?.focus());
  if (!canPerformWorkspaceOperation(access?.scopes, access?.organizationRole, props.operation)) return null;
  if (props.isInline) return <section className="workspace-panel"><h2>{props.title}</h2><ActionBody {...props} /></section>;
  return <>
    <Button id={triggerId} danger={props.isDanger} onClick={() => { setRequestId(current => current || crypto.randomUUID()); setIsOpen(true); }}>{props.title}</Button>
    {isOpen && <ActionBody key={requestId} {...props} requestId={requestId} onClose={() => { setIsOpen(false); restoreFocus(); }} onNewRequest={() => setRequestId(crypto.randomUUID())} onSuccess={() => { setIsOpen(false); setRequestId(""); restoreFocus(); }} />}
  </>;
}
