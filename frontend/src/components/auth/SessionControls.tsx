"use client";

import { Alert, Button, type ButtonProps } from "antd";
import { useActionState } from "react";
import { logoutAction, switchOrganizationAction } from "@/app/session-actions";
import { ORGANIZATION_STATUS_LABELS, type SessionResponse } from "@/lib/auth/types";

// logoutAction은 실패해도 로그인 화면으로 보내므로 돌려받을 메시지가 없다.
export function LogoutButton({ returnTo, type, size }: { returnTo?: string; type?: ButtonProps["type"]; size?: ButtonProps["size"] }) {
  const [, action, isPending] = useActionState(logoutAction, {});
  return <form action={action}>{returnTo && <input type="hidden" name="returnTo" value={returnTo} />}<Button type={type} size={size} htmlType="submit" loading={isPending}>로그아웃</Button></form>;
}

export function OrganizationPicker({ session }: { session: SessionResponse }) {
  const [state, action, isPending] = useActionState(switchOrganizationAction, {});
  return <form action={action} className="organization-picker" aria-busy={isPending}>
    <label htmlFor="organization_id">이동할 조직</label>
    <select id="organization_id" name="organization_id" defaultValue={session.organization?.id ?? ""} required>
      <option value="" disabled>조직을 선택하세요</option>
      {session.organizations.map(org => <option key={org.id} value={org.id}>{org.name} ({ORGANIZATION_STATUS_LABELS[org.status]})</option>)}
    </select>
    <Button type="primary" htmlType="submit" loading={isPending}>이동</Button>
    {state.message && <Alert type="error" showIcon title={<span role="alert">{state.message}</span>} />}
  </form>;
}
