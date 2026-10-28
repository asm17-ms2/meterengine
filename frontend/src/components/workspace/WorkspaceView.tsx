"use client";

import { Empty, Pagination, Table, Tag } from "antd";
import { ErrorAlert } from "@/components/screen/ErrorAlert";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { useState } from "react";
import { ActionPanel, type ActionPanelProps } from "@/components/workspace/ActionPanel";
import { useTenantAccess } from "@/components/auth/TenantAccess";
import { canPerformWorkspaceOperation } from "@/lib/auth/permissions";
import { formatKstDateTime } from "@/lib/format";

export function WorkspaceError({ message, status }: { message: string; status?: number }) {
  return <ErrorAlert title="데이터를 불러오지 못했습니다" message={status && status > 0 ? `${message} (${status})` : message} />;
}

export type WorkspaceColumn = { key: string; label: string; isMoney?: boolean; isNumber?: boolean; isDate?: boolean; isStatus?: boolean; statusLabels?: Record<string, string>; modeKind?: "api" | "payment"; hrefPrefix?: string; hrefKey?: string; hrefSuffix?: string };
export type WorkspaceRowActions = Record<string, ActionPanelProps[]>;

const STATUS_LABELS: Record<string, string> = {
  paid: "수납 완료", partially_canceled: "부분 취소", canceled: "전액 취소", unpaid: "결제 전", pending: "결과 확인 중", failed: "실패", done: "완료", active: "사용 중", archived: "보관됨", accepted: "수락 완료", expired: "만료", invited: "초대 중", revoked: "폐기", retry: "재시도 대기", waiting: "발송 대기", succeeded: "정상", running: "실행 중", not_required: "결제 불필요", suspended: "이용 정지", rejected: "거절", success: "성공", member: "일반 사용자", operator: "플랫폼 운영자", owner: "대표관리자", admin: "관리자",
};
const SCOPE_LABELS: Record<string, string> = { read: "조회", ingest: "사용량 수집", admin: "전체 관리" };
const JOB_LABELS: Record<string, string> = { payment_attempt_confirmation: "결제 결과 확인", payment_reconciliation: "결제 내역 대조", payment_retry: "실패 결제 재시도", webhook_delivery: "결과 통보 발송", invoice_email: "청구 안내 메일" };
const NUMBER = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 12 });

function CellValue({ value, column, row }: { value: unknown; column: WorkspaceColumn; row: Record<string, unknown> }) {
  if (column.hrefPrefix) return <Link href={`${column.hrefPrefix}${encodeURIComponent(String(row[column.hrefKey ?? "id"]))}${column.hrefSuffix ?? ""}`}>{String(value ?? "-")}</Link>;
  if (value === null || value === undefined || value === "") return <span className="muted">{column.key.startsWith("last_") && column.key.endsWith("_at") ? "기록 없음" : "-"}</span>;
  if (column.isMoney || column.isNumber || typeof value === "number") {
    const number = Number(value);
    return <span className="numeric">{Number.isFinite(number) ? NUMBER.format(number) + (column.isMoney ? "원" : "") : "확인 필요"}</span>;
  }
  if (column.isStatus) return <Tag color={["paid", "done", "active", "succeeded", "success"].includes(String(value)) ? "success" : ["failed", "rejected"].includes(String(value)) ? "error" : ["pending", "running", "retry"].includes(String(value)) ? "warning" : "default"}>{column.statusLabels?.[String(value)] ?? STATUS_LABELS[String(value)] ?? String(value)}</Tag>;
  if (column.key === "mode") return <Tag color={value === "live" ? "warning" : "default"}>{value === "live" ? column.modeKind === "api" ? "운영 키" : "실결제" : value === "test" ? column.modeKind === "api" ? "테스트 키" : "테스트 결제" : String(value)}</Tag>;
  if (Array.isArray(value)) return <span className="workspace-cell-list">{value.map(item => column.key === "scopes" ? SCOPE_LABELS[String(item)] ?? String(item) : String(item)).join(", ")}</span>;
  if (column.isDate && !Number.isFinite(Date.parse(String(value)))) return <span className="muted">시각 확인 필요</span>;
  if (column.isDate || (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value)))) return <time dateTime={String(value)} className="numeric" title={String(value)}>{formatKstDateTime(String(value))} KST</time>;
  if (column.key === "job_name") return <span>{JOB_LABELS[String(value)] ?? String(value)}<small className="field-hint technical-id">{JOB_LABELS[String(value)] ? String(value) : ""}</small></span>;
  if (typeof value === "boolean") return value ? "사용" : "중지";
  if (typeof value === "object") return <code className="workspace-json">{JSON.stringify(value)}</code>;
  return <span className="workspace-cell-text">{String(value)}</span>;
}

function RowActions({ actions }: { actions: ActionPanelProps[] }) {
  return <div className="workspace-row-actions">{actions.map((action, index) => <ActionPanel key={`${action.operation}-${index}`} {...action} />)}</div>;
}

export function WorkspaceTable({ rows, columns, rowKey = "id", empty = "등록된 항목이 없습니다.", rowActions = {}, serverPaged = false }: { rows: Record<string, unknown>[]; columns: WorkspaceColumn[]; rowKey?: string; empty?: string; rowActions?: WorkspaceRowActions; serverPaged?: boolean }) {
  const [page, setPage] = useState(1);
  const currentPage = Math.min(page, Math.max(1, Math.ceil(rows.length / 20)));
  const access = useTenantAccess();
  const hasActions = Object.values(rowActions).some(actions => actions.some(action => canPerformWorkspaceOperation(access?.scopes, access?.organizationRole, action.operation)));
  const visibleRows = serverPaged ? rows : rows.slice((currentPage - 1) * 20, currentPage * 20);
  const keyOf = (row: Record<string, unknown>) => String(row[rowKey]);
  return <>
    <div className="workspace-desktop-table"><Table dataSource={rows} rowKey={keyOf} pagination={!serverPaged && rows.length > 20 ? { current: currentPage, pageSize: 20, showSizeChanger: false, onChange: setPage } : false} scroll={{ x: "max-content" }} locale={{ emptyText: <Empty description={empty} /> }} columns={[
      ...columns.map(column => ({ title: column.label, key: column.key, dataIndex: column.key, align: column.isMoney || column.isNumber ? "right" as const : undefined, render: (value: unknown, row: Record<string, unknown>) => <CellValue value={value} column={column} row={row} /> })),
      ...(hasActions ? [{ title: "작업", key: "actions", fixed: "end" as const, render: (_: unknown, row: Record<string, unknown>) => <RowActions actions={rowActions[keyOf(row)] ?? []} /> }] : []),
    ]} /></div>
    <div className="workspace-mobile-records">
      {visibleRows.length === 0 ? <Empty description={empty} /> : visibleRows.map(row => <article key={keyOf(row)} className="workspace-mobile-record"><h3><CellValue value={row[columns[0].key]} column={columns[0]} row={row} /></h3><dl>{columns.slice(1).map(column => <div key={column.key}><dt>{column.label}</dt><dd><CellValue value={row[column.key]} column={column} row={row} /></dd></div>)}</dl>{hasActions && <RowActions actions={rowActions[keyOf(row)] ?? []} />}</article>)}
      {!serverPaged && <Pagination size="small" current={currentPage} total={rows.length} pageSize={20} showSizeChanger={false} hideOnSinglePage onChange={setPage} />}
    </div>
  </>;
}

export function WorkspaceOperations({ items }: { items: (ActionPanelProps & { id: string })[] }) {
  return <div className="workspace-actions">{items.map(item => <ActionPanel key={item.id} {...item} />)}</div>;
}
