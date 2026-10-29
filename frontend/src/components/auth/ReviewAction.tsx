"use client";

import { Alert, Button, Flex, Modal } from "antd";
import { useActionState, useId, useState } from "react";
import { reviewSignupAction, updateRoleAction } from "@/app/admin/actions";

export function ReviewAction({ id, label, target, value, kind, disabledReason }: { id: string; label: string; target: string; value: string; kind: "signup" | "role"; disabledReason?: string }) {
  const [isOpen, setIsOpen] = useState(false);
  const [state, action, isPending] = useActionState(kind === "signup" ? reviewSignupAction : updateRoleAction, {});
  const isDanger = value === "rejected" || kind === "role";
  const hintId = useId();
  return <>
    {disabledReason ? <Flex vertical gap={4} align="flex-start"><Button danger={isDanger} disabled aria-describedby={hintId}>{label}</Button><span id={hintId} className="field-hint">{disabledReason}</span></Flex> : <Button danger={isDanger} onClick={() => setIsOpen(true)}>{label}</Button>}
    <Modal className="workspace-dialog" width="min(560px, calc(100vw - 32px))" style={{ top: 24 }} styles={{ body: { maxHeight: "calc(100dvh - 200px)", overflowY: "auto" } }} title={`${label} 확인`} open={isOpen} onCancel={() => setIsOpen(false)} footer={null} closable={!isPending} mask={{ closable: !isPending }} keyboard={!isPending}>
      <p><strong>{target}</strong></p>
      <p>{kind === "signup" ? (value === "active" ? "승인하면 이 도입사의 사용자가 로그인할 수 있습니다." : "거절한 가입은 다시 승인할 수 없습니다.") : (value === "operator" ? "플랫폼 운영자는 모든 도입사의 가입과 플랫폼 권한을 관리할 수 있습니다. 도입사 내부의 관리자 추가와는 별개입니다." : "플랫폼 운영 화면 접근 권한을 없앱니다. 기존 도입사 소속이 있으면 유지되며, 소속이 없으면 도입사 업무에도 접근할 수 없습니다.")}</p>
      <form action={action}>
        <input type="hidden" name="id" value={id} /><input type="hidden" name={kind === "signup" ? "status" : "role"} value={value} />
        {state.message && <Alert type={state.done ? "success" : "error"} showIcon title={<span role={state.done ? "status" : "alert"}>{state.message}</span>} />}
        <div className="confirmation-actions"><Button onClick={() => setIsOpen(false)} disabled={isPending}>닫기</Button><Button type="primary" danger={isDanger} htmlType="submit" loading={isPending} disabled={state.done}>{label} 확정</Button></div>
      </form>
    </Modal>
  </>;
}
