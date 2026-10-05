"use client";

import { useActionState, useEffect, useState } from "react";

import { createPricePolicyAction } from "@/app/(console)/billable-metric-prices/actions";
import {
  DIMENSION_PROPERTIES_FIELD,
  rejectDimensionProperty,
} from "@/app/(console)/billable-metric-prices/dimension-properties";
import {
  PRICE_POLICY_FORM_IDLE,
  type BillableMetricPriceRowView,
} from "@/app/(console)/billable-metric-prices/state";
import { Dialog } from "@/components/screen/Dialog";

const KEY_INPUT_ID = "billable-metric-price-dimension-properties";

export function PricePolicyFormDialog({
  billableMetricPrice,
  onClose,
}: {
  billableMetricPrice: BillableMetricPriceRowView;
  onClose: () => void;
}) {
  const [state, formAction, isPending] = useActionState(
    createPricePolicyAction,
    PRICE_POLICY_FORM_IDLE,
  );
  const [keys, setKeys] = useState<string[]>([]);
  const [draft, setDraft] = useState("");
  const [inputError, setInputError] = useState<string | null>(null);
  const [isEditedSinceSubmit, setIsEditedSinceSubmit] = useState(false);

  useEffect(() => {
    if (state.status === "done") {
      onClose();
    }
  }, [state, onClose]);

  const serverFieldError =
    state.status === "invalid" && !isEditedSinceSubmit
      ? state.fieldErrors.dimension_properties
      : undefined;
  const error = inputError ?? serverFieldError;
  const message = state.status === "failed" && !isEditedSinceSubmit ? state.message : null;

  const changeKeys = (nextKeys: string[]) => {
    setKeys(nextKeys);
    setIsEditedSinceSubmit(true);
  };

  const commitDraft = (): string[] | null => {
    const rejection = rejectDimensionProperty(draft, keys);
    setDraft("");
    setInputError(rejection ?? null);
    if (rejection !== undefined) return null;
    const nextKeys = [...keys, draft];
    changeKeys(nextKeys);
    return nextKeys;
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing || event.key === "Process") return;
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      if (draft !== "") commitDraft();
      return;
    }
    if (event.key === "Backspace" && draft === "" && keys.length > 0) {
      changeKeys(keys.slice(0, -1));
    }
  };

  const handleSubmit = (formData: FormData) => {
    setInputError(null);
    if (draft !== "") {
      if (commitDraft() === null) return;
      formData.append(DIMENSION_PROPERTIES_FIELD, draft);
    }
    setIsEditedSinceSubmit(false);
    formAction(formData);
  };

  return (
    <Dialog
      labelledBy="price-policy-form-title"
      onClose={isPending ? undefined : onClose}
      action={handleSubmit}
    >
      <div className="dialog-title" id="price-policy-form-title">
        가격 정책 등록
      </div>

      <input type="hidden" name="code" value={billableMetricPrice.code} />
      {keys.map((key) => (
        <input key={key} type="hidden" name={DIMENSION_PROPERTIES_FIELD} value={key} />
      ))}

      <dl className="detail-grid" style={{ gridTemplateColumns: "88px minmax(0, 1fr)" }}>
        <dt>미터</dt>
        <dd className="detail-grid__mono">
          {billableMetricPrice.code}
          <span style={{ fontFamily: "var(--font-body)", color: "var(--text-60)", marginLeft: 8 }}>
            {billableMetricPrice.name}
          </span>
        </dd>
      </dl>

      <div className="field">
        <label htmlFor={KEY_INPUT_ID}>단가를 가르는 이벤트 속성 키</label>
        <div
          className="input"
          style={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            gap: 6,
            minHeight: 40,
            ...(error
              ? { borderColor: "var(--color-accent)", background: "var(--color-accent-100)" }
              : {}),
          }}
        >
          {keys.map((key) => (
            <span
              key={key}
              className="tag tag-neutral"
              style={{ fontFamily: "var(--mono)", gap: 6, paddingRight: 4, whiteSpace: "pre" }}
            >
              {key}
              <button
                type="button"
                aria-label={`${key} 제거`}
                disabled={isPending}
                onClick={() => changeKeys(keys.filter((candidate) => candidate !== key))}
                style={{
                  border: 0,
                  background: "transparent",
                  color: "inherit",
                  cursor: "pointer",
                  padding: "0 2px",
                  font: "inherit",
                }}
              >
                ×
              </button>
            </span>
          ))}
          <input
            id={KEY_INPUT_ID}
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
              setInputError(null);
            }}
            onKeyDown={handleKeyDown}
            placeholder={keys.length === 0 ? "키를 입력하고 Enter" : ""}
            autoComplete="off"
            spellCheck={false}
            disabled={isPending}
            autoFocus
            aria-invalid={error !== undefined}
            aria-describedby={error ? `${KEY_INPUT_ID}-error` : undefined}
            style={{
              flexGrow: 1,
              minWidth: 140,
              border: 0,
              background: "transparent",
              font: "inherit",
              fontFamily: "var(--mono)",
              fontSize: 13,
              outline: "none",
            }}
          />
        </div>
        {error ? (
          <p
            id={`${KEY_INPUT_ID}-error`}
            style={{ margin: "6px 0 0", fontSize: 12.5, color: "var(--color-accent-700)" }}
          >
            {error}
          </p>
        ) : null}
        <p className="screen-note" style={{ marginTop: 5 }}>
          비워 두고 등록하면 속성에 따라 단가가 갈리지 않는 미터가 됩니다. 키는 이벤트
          properties의 키와 같아야 합니다.
        </p>
        <p className="screen-note" style={{ marginTop: 3 }}>
          등록 뒤에는 바꿀 수 없습니다.
        </p>
      </div>

      <p
        className="screen-note"
        style={{ paddingTop: 4, borderTop: "1px solid var(--color-divider)" }}
      >
        단가는 이 화면에서 등록하지 않습니다. 단가가 없는 미터는 청구 예정액에 라인이 나오지
        않습니다.
      </p>

      {message ? (
        <p role="alert" style={{ margin: 0, fontSize: 12.5, color: "var(--color-accent-700)" }}>
          {message}
        </p>
      ) : null}

      <div className="dialog-actions">
        <button type="button" className="btn btn-secondary" disabled={isPending} onClick={onClose}>
          취소
        </button>
        <button type="submit" className="btn btn-primary" disabled={isPending}>
          {isPending ? "등록 중..." : "등록"}
        </button>
      </div>
    </Dialog>
  );
}
