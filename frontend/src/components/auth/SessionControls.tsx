"use client";

import { Alert, Button } from "antd";
import { useActionState } from "react";
import { logoutAction, switchOrganizationAction } from "@/app/session-actions";
import { ORGANIZATION_STATUS_LABELS, type SessionResponse } from "@/lib/auth/types";

export function LogoutButton({ returnTo }: { returnTo?: string }) {
  const [state, action, isPending] = useActionState(logoutAction, {});
  return <form action={action}>{returnTo && <input type="hidden" name="returnTo" value={returnTo} />}<Button htmlType="submit" loading={isPending}>로그아웃</Button>{state.message && <p role="alert">{state.message}</p>}</form>;
}

export function OrganizationPicker({ session }: { session: SessionResponse }) {
  const [state, action, isPending] = useActionState(switchOrganizationAction, {});
  return <form action={action} className="organization-picker" aria-busy={isPending}>
    <label htmlFor="organization_id">접근할 도입사</label>
    <select id="organization_id" name="organization_id" defaultValue={session.organization?.id ?? ""} required>
      <option value="" disabled>도입사를 선택하세요</option>
      {session.organizations.map(org => <option key={org.id} value={org.id}>{org.name} ({ORGANIZATION_STATUS_LABELS[org.status]})</option>)}
    </select>
    <Button type="primary" htmlType="submit" loading={isPending}>도입사로 이동</Button>
    {state.message && <Alert type="error" showIcon title={<span role="alert">{state.message}</span>} />}
  </form>;
}
