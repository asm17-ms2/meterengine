"use client";

import { useActionState, useEffect, useState } from "react";

import {
  createCustomerAction,
  updateCustomerAction,
} from "@/app/(console)/customers/actions";
import { CUSTOMER_FORM_IDLE } from "@/app/(console)/customers/state";
import type { CustomerRowView } from "@/components/customers/CustomersTable";
import { Dialog } from "@/components/screen/Dialog";

export function CustomerFormDialog({
  customer,
  onClose,
}: {
  /** 없으면 등록, 있으면 그 고객의 수정. */
  customer: CustomerRowView | null;
  onClose: () => void;
}) {
  const isEdit = customer !== null;
  const [state, formAction, isPending] = useActionState(
    isEdit ? updateCustomerAction : createCustomerAction,
    CUSTOMER_FORM_IDLE,
  );

  const [name, setName] = useState(isEdit ? customer.name : "");
  const [billingEmail, setBillingEmail] = useState(
    isEdit ? (customer.billingEmail ?? "") : "",
  );

  useEffect(() => {
    if (state.status === "done") onClose();
  }, [state, onClose]);

  const nameMessage =
    (state.status === "invalid" && state.field === "name") ||
    state.status === "failed"
      ? state.message
      : null;
  const billingEmailMessage =
    state.status === "invalid" && state.field === "billingEmail"
      ? state.message
      : null;

  return (
    <Dialog
      labelledBy="customer-form-title"
      onClose={isPending ? undefined : onClose}
      action={formAction}
    >
      <div className="dialog-title" id="customer-form-title">
        {isEdit ? "고객 수정" : "고객 등록"}
      </div>

      {isEdit ? <input type="hidden" name="id" value={customer.id} /> : null}

      <div className="field">
        <label htmlFor="customer-name">
          고객명 <span style={{ color: "var(--color-accent)" }}>*</span>
        </label>
        <input
          id="customer-name"
          name="name"
          className="input"
          value={name}
          onChange={(event) => setName(event.target.value)}
          disabled={isPending}
          autoFocus
          maxLength={255}
          aria-invalid={nameMessage !== null}
          aria-describedby={nameMessage ? "customer-name-error" : undefined}
          style={
            nameMessage
              ? {
                  borderColor: "var(--color-accent)",
                  background: "var(--color-accent-100)",
                }
              : { borderColor: "var(--color-accent)" }
          }
        />
        {nameMessage ? (
          <p
            id="customer-name-error"
            style={{
              margin: "6px 0 0",
              fontSize: 12.5,
              color: "var(--color-accent-700)",
            }}
          >
            {nameMessage}
          </p>
        ) : null}
      </div>

      <div className="field">
        <label htmlFor="customer-billing-email">청구 이메일</label>
        <input
          id="customer-billing-email"
          name="billing_email"
          type="text"
          inputMode="email"
          autoComplete="email"
          spellCheck={false}
          className="input"
          value={billingEmail}
          onChange={(event) => setBillingEmail(event.target.value)}
          disabled={isPending}
          maxLength={254}
          placeholder="billing@example.com"
          aria-invalid={billingEmailMessage !== null}
          aria-describedby={
            billingEmailMessage
              ? "customer-billing-email-error"
              : "customer-billing-email-help"
          }
          style={
            billingEmailMessage
              ? {
                  borderColor: "var(--color-accent)",
                  background: "var(--color-accent-100)",
                }
              : undefined
          }
        />
        {billingEmailMessage ? (
          <p
            id="customer-billing-email-error"
            style={{
              margin: "6px 0 0",
              fontSize: 12.5,
              color: "var(--color-accent-700)",
            }}
          >
            {billingEmailMessage}
          </p>
        ) : (
          <p
            id="customer-billing-email-help"
            style={{
              margin: "6px 0 0",
              fontSize: 12.5,
              color: "var(--text-60)",
            }}
          >
            확정한 인보이스의 청구 메일을 받을 주소입니다. 비워 둘 수 있습니다.
          </p>
        )}
      </div>

      {isEdit ? (
        <dl
          className="detail-grid"
          style={{ gridTemplateColumns: "88px minmax(0, 1fr)" }}
        >
          <dt>고객 ID</dt>
          <dd className="detail-grid__mono">{customer.id}</dd>
          <dt>등록일</dt>
          <dd>{customer.createdAt}</dd>
        </dl>
      ) : null}

      <div className="dialog-actions">
        <button
          type="button"
          className="btn btn-secondary"
          disabled={isPending}
          onClick={onClose}
        >
          취소
        </button>
        <button type="submit" className="btn btn-primary" disabled={isPending}>
          {isPending ? "저장 중..." : "저장"}
        </button>
      </div>
    </Dialog>
  );
}
