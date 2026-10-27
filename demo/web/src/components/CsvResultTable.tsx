"use client";

import { Space, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";

import type { RowResult } from "@/lib/send-results";

const TAG_COLORS: [RowResult["tag"], string][] = [
  ["NEW", "green"],
  ["DUP", "gold"],
  ["REJ", "red"],
  ["SKIP", "default"],
  ["UNKNOWN", "orange"],
  ["NOT_SENT", "default"],
];

const TAG_LABELS = new Map<RowResult["tag"], string>([
  ["NEW", "접수 확인"],
  ["DUP", "중복 접수 확인"],
  ["REJ", "거절 확인"],
  ["SKIP", "검증 제외"],
  ["UNKNOWN", "결과 미확인"],
  ["NOT_SENT", "미전송"],
]);

function colorOf(tag: RowResult["tag"]): string {
  return TAG_COLORS.find(([candidate]) => candidate === tag)?.[1] ?? "default";
}

const COLUMNS: TableColumnsType<RowResult> = [
  { title: "줄", dataIndex: "line", width: 70 },
  { title: "transaction_id", dataIndex: "transaction_id" },
  { title: "결과", dataIndex: "tag", width: 130, render: (tag: RowResult["tag"]) => <Tag color={colorOf(tag)}>{TAG_LABELS.get(tag)}</Tag> },
  { title: "code", dataIndex: "code" },
  { title: "메시지", dataIndex: "message" },
];

export function CsvResultTable({ results }: { results: RowResult[] }) {
  return (
    <Space orientation="vertical" style={{ width: "100%" }}>
      <p role="status" aria-live="polite">전송 결과: {TAG_COLORS.map(([tag]) => `${TAG_LABELS.get(tag)} ${results.filter(result => result.tag === tag).length}건`).join(", ")}</p>
      <Space wrap aria-hidden="true">
        {TAG_COLORS.map(([tag, color]) => (
          <Tag key={tag} color={color}>
            {TAG_LABELS.get(tag)} {results.filter((result) => result.tag === tag).length}
          </Tag>
        ))}
      </Space>
      <Table<RowResult> size="small" rowKey="line" columns={COLUMNS} dataSource={results} pagination={{ pageSize: 20 }} scroll={{ x: "max-content" }} />
    </Space>
  );
}
