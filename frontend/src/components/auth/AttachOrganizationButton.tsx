"use client";

import { Alert, App, Button, Checkbox, Flex, Modal } from "antd";
import { startTransition, useActionState, useEffect, useId, useRef, useState, type FormEvent } from "react";
import { attachOrganizationAction } from "@/app/admin/actions";
import { ACCESS_SCOPE_OPTIONS } from "@/lib/auth/permissions";
import type { ActionState, SessionOrganization } from "@/lib/auth/types";

export function AttachOrganizationButton({ userId, userLabel, organizations: activeOrganizations, memberOrganizationIds }: { userId: string; userLabel: string; organizations: SessionOrganization[]; memberOrganizationIds: string[] }) {
  const organizations = activeOrganizations.filter(organization => !memberOrganizationIds.includes(organization.id));
  const { message } = App.useApp();
  const [isOpen, setIsOpen] = useState(false);
  const [isDirty, setIsDirty] = useState(false);
  const [state, action, isPending] = useActionState(async (previous: ActionState, data: FormData) => {
    const result = await attachOrganizationAction(previous, data);
    if (result.done) {
      setIsDirty(false);
      message.success(result.message ?? "도입사 소속과 업무 권한을 연결했습니다.");
    }
    return result;
  }, {});
  const formId = useId();
  const feedbackRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (state.message) feedbackRef.current?.focus();
  }, [state]);

  function close() {
    if (isPending || (isDirty && !window.confirm("작성 중인 소속 연결을 버리고 닫을까요?"))) return;
    setIsOpen(false);
    setIsDirty(false);
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    startTransition(() => action(data));
  }

  return <>
    {organizations.length === 0 ? <Flex vertical gap={4} align="flex-start"><Button disabled aria-describedby={`${formId}-empty`}>도입사 소속 연결</Button><span id={`${formId}-empty`} className="field-hint">추가로 연결할 사용 중인 도입사가 없습니다.</span></Flex> : <Button onClick={() => setIsOpen(true)}>도입사 소속 연결</Button>}
    <Modal title="도입사 소속 연결" open={isOpen} onCancel={close} keyboard={!isPending} mask={{ closable: !isPending }} closable={{ disabled: isPending }} className="workspace-dialog" width="min(600px, calc(100vw - 32px))" style={{ top: 24 }} styles={{ body: { maxHeight: "calc(100dvh - 210px)", overflowY: "auto", padding: 8, marginInline: -8 } }} footer={<div className="workspace-dialog-actions"><Button onClick={close} disabled={isPending}>닫기</Button><Button form={formId} type="primary" htmlType="submit" loading={isPending} disabled={state.done}>소속 연결</Button></div>}>
      <form id={formId} onSubmit={submit} onChange={() => setIsDirty(true)} className="workspace-form" aria-busy={isPending}>
        <input name="user_account_id" type="hidden" value={userId} />
        <p><strong>{userLabel}</strong>의 도입사 업무 권한을 지정합니다. 플랫폼 운영 권한은 바뀌지 않습니다.</p>
        <fieldset disabled={isPending || state.done}>
          <label className="workspace-field"><span>연결할 도입사 (필수)</span><select name="organization_id" required defaultValue=""><option value="">도입사를 선택하세요</option>{organizations.map(organization => <option key={organization.id} value={organization.id}>{organization.name}</option>)}</select></label>
          <fieldset className="workspace-choice-group" aria-describedby={`${formId}-scope-hint`}><legend>도입사 업무 권한 (필수)</legend><Checkbox.Group name="scopes" defaultValue={["read"]} options={ACCESS_SCOPE_OPTIONS} aria-label="도입사 업무 권한" /><small id={`${formId}-scope-hint`} className="field-hint">기본값은 조회입니다. 전체 관리는 조회와 수집을 포함하며, 대표관리자의 사용자 관리는 포함하지 않습니다.</small></fieldset>
        </fieldset>
        {state.message && <div ref={feedbackRef} tabIndex={-1}><Alert showIcon type={state.done ? "success" : "error"} title={state.message} /></div>}
      </form>
    </Modal>
  </>;
}
