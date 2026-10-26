"use client";

import { App, Badge, Button, Flex, Popconfirm, Table, Tag, theme, Typography } from "antd";
import {
  startTransition,
  useActionState,
  useCallback,
  useId,
  useMemo,
  useRef,
  useState,
} from "react";

import { deletePriceRateAction } from "@/app/(console)/billable-metric-prices/actions";
import {
  PRICE_RATE_DELETE_IDLE,
  type BillableMetricPriceRowView,
  type PriceRateDeleteState,
  type PriceRateRowView,
} from "@/app/(console)/billable-metric-prices/state";
import { focusInFrames } from "@/components/screen/use-initial-focus";
import { countDimensionSlots, DimensionValues } from "@/components/table/DimensionValues";
import { NumberText } from "@/components/table/NumberText";
import { koreanCompare, TABLE_STICKY } from "@/components/table/table-defaults";
import {
  type ClientSortSelectionState,
  type SortableColumn,
  useTableSort,
} from "@/components/table/table-sort";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

const MIN_WIDTH = 940;

type MetricRow = { kind: "metric"; key: string; metric: BillableMetricPriceRowView };

type RateRow = {
  kind: "rate";
  key: string;
  metric: BillableMetricPriceRowView;
  priceRate: PriceRateRowView;
};

type PriceRow = MetricRow | RateRow;

function toRateKey(code: string, priceRate: PriceRateRowView): string {
  return `${code}/${JSON.stringify(priceRate.dimensionValues)}`;
}

function toPriceRateDeleteFormData(code: string, priceRate: PriceRateRowView): FormData {
  const formData = new FormData();
  formData.set("code", code);
  formData.set("dimension_values", JSON.stringify(priceRate.dimensionValues));
  return formData;
}

function flattenRows(metricRows: MetricRow[]): PriceRow[] {
  return metricRows.flatMap((metricRow): PriceRow[] => [
    metricRow,
    ...metricRow.metric.priceRates.map(
      (priceRate): RateRow => ({
        kind: "rate",
        key: toRateKey(metricRow.metric.code, priceRate),
        metric: metricRow.metric,
        priceRate,
      }),
    ),
  ]);
}

function PriceRateDeleteButton({
  metric,
  priceRate,
  onSettled,
}: {
  metric: BillableMetricPriceRowView;
  priceRate: PriceRateRowView;
  onSettled: (rateKey: string, failureMessage: string | undefined) => void;
}) {
  const { message } = App.useApp();
  const rateKey = toRateKey(metric.code, priceRate);
  const [, formAction, isPending] = useActionState(
    async (previous: PriceRateDeleteState, formData: FormData): Promise<PriceRateDeleteState> => {
      const next = await deletePriceRateAction(previous, formData);
      if (next.status === "failed") {
        onSettled(rateKey, next.message);
      } else {
        onSettled(rateKey, undefined);
        message.success("삭제했습니다");
      }
      return next;
    },
    PRICE_RATE_DELETE_IDLE,
  );
  const target = [metric.name, priceRate.combination].filter(Boolean).join(" ");
  const cancelButtonId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);

  const moveFocus = (isOpen: boolean) => {
    if (isOpen) {
      focusInFrames(() => document.getElementById(cancelButtonId));
      return;
    }
    requestAnimationFrame(() => {
      const activeElement = document.activeElement;
      const isFocusLost =
        activeElement === null ||
        activeElement === document.body ||
        activeElement.closest(".ant-popconfirm") !== null;
      if (isFocusLost) triggerRef.current?.focus();
    });
  };

  return (
    <Popconfirm
      title="이 단가를 삭제할까요?"
      description={target}
      okText="삭제"
      cancelText="취소"
      okButtonProps={{ danger: true, loading: isPending }}
      cancelButtonProps={{ id: cancelButtonId }}
      onOpenChange={moveFocus}
      onConfirm={() =>
        startTransition(() => formAction(toPriceRateDeleteFormData(metric.code, priceRate)))
      }
    >
      <Button
        ref={triggerRef}
        type="link"
        size="small"
        danger
        disabled={isPending}
        aria-label={[metric.name, priceRate.combination, "단가 삭제"].filter(Boolean).join(" ")}
      >
        {isPending ? "삭제 중..." : "삭제"}
      </Button>
    </Popconfirm>
  );
}

function PriceRateRegisterButton({
  row,
  onRegisterRate,
}: {
  row: BillableMetricPriceRowView;
  onRegisterRate: (row: BillableMetricPriceRowView) => void;
}) {
  return (
    <Button
      type="link"
      size="small"
      onClick={() => onRegisterRate(row)}
      aria-label={`${row.name} 단가 등록`}
    >
      단가 등록
    </Button>
  );
}

export function BillableMetricPricesTable({
  query,
  capabilities,
  rows,
  onRegisterRate,
  clientSortSelection,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  rows: BillableMetricPriceRowView[];
  onRegisterRate: (row: BillableMetricPriceRowView) => void;
  clientSortSelection?: ClientSortSelectionState;
}) {
  const { token } = theme.useToken();
  const [failureByRateKey, setFailureByRateKey] = useState<Record<string, string>>({});

  const settleDelete = useCallback((rateKey: string, failureMessage: string | undefined) => {
    setFailureByRateKey((previous) => {
      const next = { ...previous };
      if (failureMessage === undefined) delete next[rateKey];
      else next[rateKey] = failureMessage;
      return next;
    });
  }, []);

  const slotCount = useMemo(
    () =>
      countDimensionSlots(
        rows.flatMap((row) => row.priceRates.map((priceRate) => priceRate.dimensionValues)),
      ),
    [rows],
  );

  const monoStyle = { fontFamily: token.fontFamilyCode };
  const metricRowStyle = { background: token.colorFillQuaternary };
  const metricFixedCellStyle = {
    backgroundImage: `linear-gradient(${token.colorFillQuaternary}, ${token.colorFillQuaternary})`,
  };

  const sortableColumns: SortableColumn<PriceRow>[] = [
    {
      key: "code",
      title: "코드",
      width: 195,
      ellipsis: true,
      sortKey: "code",
      clientSorter: (left, right) => koreanCompare(left.metric.code, right.metric.code),
      render: (_, row) =>
        row.kind === "metric" ? (
          <Typography.Text strong style={monoStyle}>
            {row.metric.code}
          </Typography.Text>
        ) : null,
    },
    {
      key: "name",
      title: "이름",
      width: 130,
      ellipsis: true,
      sortKey: "name",
      clientSorter: (left, right) => koreanCompare(left.metric.name, right.metric.name),
      render: (_, row) => (row.kind === "metric" ? row.metric.name : null),
    },
    {
      key: "dimensionProperties",
      title: "차원 키",
      width: 110,
      render: (_, row) => {
        if (row.kind !== "metric") return null;
        if (row.metric.dimensionProperties.length === 0) {
          return <Typography.Text type="secondary">없음</Typography.Text>;
        }
        return (
          <Flex wrap gap={4}>
            {row.metric.dimensionProperties.map((dimensionProperty) => (
              <Tag key={dimensionProperty} style={{ ...monoStyle, marginInlineEnd: 0 }}>
                {dimensionProperty}
              </Tag>
            ))}
          </Flex>
        );
      },
    },
    {
      key: "combination",
      title: "조합",
      render: (_, row) => {
        if (row.kind === "metric") {
          return row.metric.status === "no_rate" ? (
            <Badge status="warning" text="단가 없음" />
          ) : (
            <Badge status="success" text="청구 중" />
          );
        }
        const failureMessage = failureByRateKey[row.key];
        return (
          <DimensionValues dimensionValues={row.priceRate.dimensionValues} slotCount={slotCount}>
            {failureMessage ? (
              <Typography.Text type="danger" role="alert">
                {failureMessage}
              </Typography.Text>
            ) : null}
          </DimensionValues>
        );
      },
    },
    {
      key: "priceRateCount",
      title: "단가",
      width: 120,
      align: "right",
      clientSorter: (left, right) => left.metric.priceRates.length - right.metric.priceRates.length,
      render: (_, row) =>
        row.kind === "metric" ? (
          <Typography.Text type="secondary">단가 {row.metric.priceRates.length}개</Typography.Text>
        ) : (
          <NumberText strong>{row.priceRate.unitPrice}</NumberText>
        ),
    },
    {
      key: "actions",
      title: "작업",
      width: 110,
      align: "right",
      fixed: "end",
      onCell: (row) => (row.kind === "metric" ? { style: metricFixedCellStyle } : {}),
      render: (_, row) =>
        row.kind === "metric" ? (
          <PriceRateRegisterButton row={row.metric} onRegisterRate={onRegisterRate} />
        ) : (
          <PriceRateDeleteButton
            metric={row.metric}
            priceRate={row.priceRate}
            onSettled={settleDelete}
          />
        ),
    },
  ];

  const { columns, onChange, sortState } = useTableSort(query, capabilities, sortableColumns, {
    isClientSortManual: true,
    clientSortSelection,
  });

  const metricRows = rows.map((metric): MetricRow => ({ kind: "metric", key: metric.code, metric }));
  const dataSource = flattenRows(
    sortState.compare ? [...metricRows].sort(sortState.compare) : metricRows,
  );

  return (
    <Table<PriceRow>
      rowKey="key"
      columns={columns}
      dataSource={dataSource}
      onChange={onChange}
      pagination={false}
      size="medium"
      sticky={TABLE_STICKY}
      tableLayout="fixed"
      scroll={{ x: MIN_WIDTH }}
      onRow={(row) =>
        row.kind === "metric" ? { style: metricRowStyle } : {}
      }
    />
  );
}
