"use client";

import { useActionState, useEffect, useState } from "react";

import { createPriceRateAction } from "@/app/(console)/billable-metric-prices/actions";
import {
  PRICE_RATE_FORM_IDLE,
  type BillableMetricPriceRowView,
} from "@/app/(console)/billable-metric-prices/state";
import { Dialog } from "@/components/screen/Dialog";

const UNIT_PRICE_FIELD = "unit_price";
const MONO_INPUT_STYLE = { fontFamily: "var(--mono)", fontSize: 13 } as const;

export function PriceRateFormDialog({
  billableMetricPrice,
  onClose,
}: {
  billableMetricPrice: BillableMetricPriceRowView;
  onClose: () => void;
}) {
  const [state, formAction, isPending] = useActionState(createPriceRateAction, PRICE_RATE_FORM_IDLE);
  const [values, setValues] = useState<Record<string, string>>({});
  const { dimensionProperties } = billableMetricPrice;

  useEffect(() => {
    if (state.status === "done") onClose();
  }, [state, onClose]);

  const buildInputProps = (field: string, autoFocus: boolean) => ({
    className: "input",
    value: values[field] ?? "",
    onChange: (event: React.ChangeEvent<HTMLInputElement>) =>
      setValues((prev) => ({ ...prev, [field]: event.target.value })),
    autoComplete: "off",
    disabled: isPending,
    autoFocus,
    style: MONO_INPUT_STYLE,
  });

  return (
    <Dialog
      labelledBy="price-rate-form-title"
      onClose={isPending ? undefined : onClose}
      action={formAction}
    >
      <div className="dialog-title" id="price-rate-form-title">{billableMetricPrice.name} 단가 등록</div>
      <input type="hidden" name="code" value={billableMetricPrice.code} />

      {dimensionProperties.map((dimensionProperty, index) => (
        <div key={dimensionProperty} className="field">
          <label htmlFor={`price-rate-dimension-${dimensionProperty}`}>{dimensionProperty} 값</label>
          <input type="hidden" name="dimension_key" value={dimensionProperty} />
          <input
            id={`price-rate-dimension-${dimensionProperty}`}
            name="dimension_value"
            placeholder="claude-opus-5"
            {...buildInputProps(dimensionProperty, index === 0)}
          />
        </div>
      ))}

      <div className="field">
        <label htmlFor="price-rate-unit-price">단가 (원)</label>
        <input
          id="price-rate-unit-price"
          name={UNIT_PRICE_FIELD}
          placeholder="0.5"
          inputMode="decimal"
          {...buildInputProps(UNIT_PRICE_FIELD, dimensionProperties.length === 0)}
        />
      </div>

      <p className="screen-note" style={{ paddingTop: 4, borderTop: "1px solid var(--color-divider)" }}>
        같은 조합의 단가가 이미 있으면 새 단가로 덮어씁니다.
      </p>

      {state.status === "failed" ? (
        <p role="alert" style={{ margin: 0, fontSize: 12.5, color: "var(--color-accent-700)" }}>{state.message}</p>
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
