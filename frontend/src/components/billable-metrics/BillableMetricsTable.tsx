"use client";

import { useTenantScope } from "@/components/auth/TenantAccess";

import { Button, Flex, Table, Tag, theme, Typography } from "antd";

import { ActionPanel } from "@/components/workspace/ActionPanel";
import type { BillableMetricRowView } from "@/app/(console)/billable-metrics/state";
import { koreanCompare, TABLE_STICKY } from "@/components/table/table-defaults";
import {
  type ClientSortSelectionState,
  type SortableColumn,
  useTableSort,
} from "@/components/table/table-sort";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";
import { metricAggregationLabel, metricCalculationFormula } from "@/lib/billable-metric-calculation";

const MIN_WIDTH = 1340;

function DimensionPropertyTags({
  dimensionProperties,
  fontFamily,
}: {
  dimensionProperties: string[];
  fontFamily: string;
}) {
  if (dimensionProperties.length === 0) {
    return <Typography.Text type="secondary">없음</Typography.Text>;
  }
  return (
    <Flex wrap gap={4}>
      {dimensionProperties.map((dimensionProperty) => (
        <Tag key={dimensionProperty} style={{ marginInlineEnd: 0, fontFamily }}>
          {dimensionProperty}
        </Tag>
      ))}
    </Flex>
  );
}

export function BillableMetricsTable({
  rows,
  query,
  capabilities,
  onEdit,
  onDelete,
  clientSortSelection,
}: {
  rows: BillableMetricRowView[];
  query: ListQuery;
  capabilities: ListCapabilities;
  onEdit: (row: BillableMetricRowView) => void;
  onDelete: (row: BillableMetricRowView) => void;
  clientSortSelection?: ClientSortSelectionState;
}) {
  const { token } = theme.useToken();
  const canManage = useTenantScope("admin");
  const monoStyle = { fontFamily: token.fontFamilyCode };

  const sortableColumns: SortableColumn<BillableMetricRowView>[] = [
    {
      key: "code",
      title: "코드",
      width: 195,
      ellipsis: true,
      sortKey: "code",
      clientSorter: (left, right) => koreanCompare(left.code, right.code),
      render: (_, row) => (
        <Typography.Text strong style={monoStyle}>
          {row.code}
        </Typography.Text>
      ),
    },
    {
      key: "name",
      title: "이름",
      ellipsis: true,
      sortKey: "name",
      clientSorter: (left, right) => koreanCompare(left.name, right.name),
      render: (_, row) => row.name,
    },
    {
      key: "eventType",
      title: "이벤트 타입",
      width: 145,
      ellipsis: true,
      sortKey: "event_type",
      clientSorter: (left, right) => koreanCompare(left.eventType, right.eventType),
      render: (_, row) => (
        <Typography.Text type="secondary" style={monoStyle}>
          {row.eventType}
        </Typography.Text>
      ),
    },
    {
      key: "aggregation",
      title: "집계 / 변환",
      width: 240,
      render: (_, row) => <><span>{metricAggregationLabel(row.aggregation, row.percentile)}</span><p className="field-hint" style={{ overflowWrap: "anywhere" }}>{metricCalculationFormula({ aggregation: row.aggregation, target_property: row.targetProperty, percentile: row.percentile, unit_size: row.unitSize, rounding_mode: row.roundingMode, rounding_precision: row.roundingPrecision })}</p></>,
    },
    {
      key: "targetProperty",
      title: "집계 대상 속성",
      width: 195,
      ellipsis: true,
      render: (_, row) => (
        <Typography.Text type="secondary" style={monoStyle}>
          {row.targetProperty ?? "속성 불필요"}
        </Typography.Text>
      ),
    },
    {
      key: "dimensionProperties",
      title: "차원 키",
      width: 100,
      render: (_, row) => (
        <DimensionPropertyTags
          dimensionProperties={row.dimensionProperties}
          fontFamily={token.fontFamilyCode}
        />
      ),
    },
    { key: "managementStatus", title: "관리 상태 / 적용", width: 170, render: (_, row) => <><Tag color={row.archivedAt ? "default" : "success"}>{row.archivedAt ? "보관됨" : "사용 중"}</Tag>{row.inactiveFrom && <p className="field-hint">{row.inactiveFrom}부터 집계 중단</p>}{row.isReactivationScheduled && <p className="field-hint">{row.activeFrom}부터 집계 재개</p>}</> },
    {
      key: "actions",
      title: "작업",
      width: 110,
      align: "right",
      fixed: "end",
      render: (_, row) => (
        <Flex justify="end" gap={4}>
          {row.archivedAt ? <ActionPanel title="미터 복원" operation="restoreBillableMetric" description={`${row.name} 미터를 다시 사용합니다. 적용 월과 과거 사용량은 보존됩니다.`} fields={[{ name: "code", label: "", type: "hidden", value: row.code }]} /> : <><Button
            type="link"
            size="small"
            aria-label={`${row.name} 수정`}
            onClick={() => onEdit(row)}
          >
            수정
          </Button>
          <Button
            type="link"
            size="small"
            danger
            aria-label={`${row.name} 보관`}
            onClick={() => onDelete(row)}
          >
            보관
          </Button></>}
        </Flex>
      ),
    },
  ];

  const { columns, onChange } = useTableSort(query, capabilities, sortableColumns.filter(column => column.key !== "actions" || canManage), {
    clientSortSelection,
  });

  return (
    <Table<BillableMetricRowView>
      rowKey="code"
      columns={columns}
      dataSource={rows}
      onChange={onChange}
      pagination={false}
      size="medium"
      sticky={TABLE_STICKY}
      tableLayout="fixed"
      scroll={{ x: MIN_WIDTH }}
    />
  );
}
