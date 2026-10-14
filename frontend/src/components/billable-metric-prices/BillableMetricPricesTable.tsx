"use client";

import { useActionState } from "react";

import { deletePriceRateAction } from "@/app/(console)/billable-metric-prices/actions";
import {
  PRICE_RATE_DELETE_IDLE,
  type BillableMetricPriceRowView,
  type PriceRateRowView,
} from "@/app/(console)/billable-metric-prices/state";
import { countDimensionSlots, DimensionValuesCell } from "@/components/table/DimensionValuesCell";
import { GridCell } from "@/components/table/GridCell";
import { GridHead } from "@/components/table/GridHead";
import { GridRow } from "@/components/table/GridRow";
import { GridTable } from "@/components/table/GridTable";

const COLUMNS = "190px minmax(0, 1fr) 200px minmax(0, 1fr) 140px 110px";
const MIN_WIDTH = 1040;

const HEAD_LABELS = [
  "코드",
  "이름",
  "차원 키",
  "조합",
  { label: "단가", right: true },
  { label: "작업", right: true },
] as const;

function DimensionPropertiesCell({ dimensionProperties }: { dimensionProperties: string[] }) {
  if (dimensionProperties.length === 0) {
    return <GridCell className="grid-cell--muted">없음</GridCell>;
  }
  return (
    <GridCell>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
        {dimensionProperties.map((dimensionProperty) => (
          <span key={dimensionProperty} className="tag tag-neutral grid-cell--mono">
            {dimensionProperty}
          </span>
        ))}
      </div>
    </GridCell>
  );
}

function PriceRateRow({
  row,
  priceRate,
  slotCount,
}: {
  row: BillableMetricPriceRowView;
  priceRate: PriceRateRowView;
  slotCount: number;
}) {
  const [state, formAction, isPending] = useActionState(
    deletePriceRateAction,
    PRICE_RATE_DELETE_IDLE,
  );
  return (
    <GridRow columns={COLUMNS} className="grid-row--actions">
      <GridCell />
      <GridCell />
      <GridCell />
      <DimensionValuesCell
        dimensionValues={priceRate.dimensionValues}
        slotCount={slotCount}
        className="grid-cell--child"
      >
        {state.status === "failed" ? (
          <span role="alert" style={{ color: "var(--color-accent-700)" }}>
            {state.message}
          </span>
        ) : null}
      </DimensionValuesCell>
      <GridCell className="grid-cell--num grid-cell--right grid-cell--strong">
        {priceRate.unitPrice}
      </GridCell>
      <GridCell className="grid-cell--actions">
        <form action={formAction}>
          <input type="hidden" name="code" value={row.code} />
          <input
            type="hidden"
            name="dimension_values"
            value={JSON.stringify(priceRate.dimensionValues)}
          />
          <button
            type="submit"
            className="btn btn-ghost"
            style={{ fontSize: 13 }}
            disabled={isPending}
            aria-label={[row.name, priceRate.combination, "단가 삭제"]
              .filter(Boolean)
              .join(" ")}
          >
            {isPending ? "삭제 중..." : "삭제"}
          </button>
        </form>
      </GridCell>
    </GridRow>
  );
}

export function BillableMetricPricesTable({
  rows,
  onRegisterRate,
}: {
  rows: BillableMetricPriceRowView[];
  onRegisterRate: (row: BillableMetricPriceRowView) => void;
}) {
  const slotCount = countDimensionSlots(
    rows.flatMap((row) => row.priceRates.map((priceRate) => priceRate.dimensionValues)),
  );

  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD_LABELS} />
      {rows.map((row) => (
        <div key={row.code}>
          <GridRow columns={COLUMNS} className="grid-row--actions grid-row--group">
            <GridCell className="grid-cell--mono grid-cell--strong grid-cell--truncate">
              {row.code}
            </GridCell>
            <GridCell className="grid-cell--truncate">{row.name}</GridCell>
            <DimensionPropertiesCell dimensionProperties={row.dimensionProperties} />
            {row.status === "no_rate" ? (
              <GridCell className="grid-cell--strong">
                <span style={{ color: "var(--color-accent-700)" }}>단가 없음</span>
              </GridCell>
            ) : (
              <GridCell className="grid-cell--muted">청구 중</GridCell>
            )}
            <GridCell className="grid-cell--right grid-cell__count">
              단가 {row.priceRates.length}개
            </GridCell>
            <GridCell className="grid-cell--actions">
              <button
                type="button"
                className="btn btn-ghost"
                style={{ fontSize: 13 }}
                onClick={() => onRegisterRate(row)}
                aria-label={`${row.name} 단가 등록`}
              >
                단가 등록
              </button>
            </GridCell>
          </GridRow>
          {row.priceRates.map((priceRate) => (
            <PriceRateRow
              key={JSON.stringify(priceRate.dimensionValues)}
              row={row}
              priceRate={priceRate}
              slotCount={slotCount}
            />
          ))}
        </div>
      ))}
    </GridTable>
  );
}
