"use client";

import { UploadOutlined } from "@ant-design/icons";
import { Alert, Button, Select, Space, Table, Tag, Typography, Upload } from "antd";
import type { TableColumnsType } from "antd";
import { useActionState, useState } from "react";

import { sendEventCsvAction, type CsvSendState } from "@/app/events/csv/actions";
import { CsvResultTable } from "@/components/CsvResultTable";
import { ErrorViewAlert } from "@/components/ErrorViewAlert";
import { parseEventCsv, type CsvEventRow } from "@/lib/csv";

const INITIAL_STATE: CsvSendState = { status: "idle" };

const CSV_MAX_BYTES = 900 * 1024;

const PREVIEW_COLUMNS: TableColumnsType<CsvEventRow> = [
  { title: "줄", dataIndex: "line", width: 70 },
  { title: "transaction_id", dataIndex: "transaction_id" },
  { title: "customer_id", dataIndex: "customer_id" },
  { title: "event_type", dataIndex: "event_type" },
  { title: "timestamp", dataIndex: "timestamp" },
  { title: "properties", dataIndex: "properties_text" },
  { title: "note", dataIndex: "note" },
  {
    title: "확인",
    dataIndex: "error",
    render: (error: string | undefined) => (error === undefined ? null : <Tag color="red">{error}</Tag>),
  },
];

type CsvUploadPanelProps = {
  targetNames: string[];
  configError?: string;
};

type CsvFile = { name: string; text: string };

export function CsvUploadPanel({ targetNames, configError }: CsvUploadPanelProps) {
  const [file, setFile] = useState<CsvFile | null>(null);
  const [isTooLarge, setIsTooLarge] = useState(false);
  const [target, setTarget] = useState<string | undefined>(targetNames[0]);
  const [state, formAction, isPending] = useActionState(
    (_previous: CsvSendState, formData: FormData) => sendEventCsvAction(formData),
    INITIAL_STATE,
  );

  const parsed = file === null ? null : parseEventCsv(file.text);
  const rows = parsed !== null && "rows" in parsed ? parsed.rows : [];
  const sendableCount = rows.filter((row) => row.error === undefined).length;

  return (
    <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
      <Typography.Title level={3}>CSV 보내기</Typography.Title>
      {configError === undefined ? null : <Alert type="error" title="대상 설정을 읽지 못했습니다" description={configError} />}
      <Alert type="info" title="예제 양식으로 CSV를 준비하세요" description={<><p>필수 컬럼: transaction_id, customer_id, event_type, timestamp, properties. properties는 JSON 객체이며 900KB 이하 파일을 선택하세요.</p><a download="meterengine-events.csv" href={'data:text/csv;charset=utf-8,' + encodeURIComponent('transaction_id,customer_id,event_type,timestamp,properties\nexample-001,customer-id,usage,2026-10-26T12:00:00%2B09:00,"{""units"":10}"\n'.replace('%2B', '+'))}>예제 CSV 받기</a><p>예제의 고객 ID, 유형과 시각을 실제 시연 대상에 맞춰 바꾸세요. 잘못된 행은 건너뛰고 나머지만 전송합니다.</p></>} />
      <Upload
        accept=".csv,text/csv"
        showUploadList={false}
        beforeUpload={async (selected) => {
          if (selected.size > CSV_MAX_BYTES) {
            setFile(null);
            setIsTooLarge(true);
            return false;
          }
          setIsTooLarge(false);
          setFile({ name: selected.name, text: await selected.text() });
          return false;
        }}
      >
        <Button icon={<UploadOutlined />}>CSV 파일 고르기</Button>
      </Upload>
      {isTooLarge ? <Alert type="error" title="CSV 파일이 900KB를 넘어 보낼 수 없습니다. 나눠서 올리세요" /> : null}
      {file === null ? null : <Typography.Text>{file.name}</Typography.Text>}
      {parsed !== null && "error" in parsed ? <Alert type="error" title={parsed.error} /> : null}
      {rows.length === 0 ? null : (
        <>
          <Typography.Text>
            {rows.length}행, 보낼 행 {sendableCount}, 건너뛸 행 {rows.length - sendableCount}
          </Typography.Text>
          <Table<CsvEventRow>
            size="small"
            rowKey="line"
            columns={PREVIEW_COLUMNS}
            dataSource={rows}
            pagination={{ pageSize: 20 }}
            scroll={{ x: "max-content" }}
          />
        </>
      )}
      <form action={formAction}>
        <input type="hidden" name="csv" value={file?.text ?? ""} />
        <input type="hidden" name="target" value={target ?? ""} />
        <Space>
          <Select
            style={{ minWidth: 160 }}
            aria-label="CSV 전송 대상"
            placeholder="연동 대상"
            value={target}
            onChange={setTarget}
            options={targetNames.map((name) => ({ value: name, label: name }))}
          />
          <Button type="primary" htmlType="submit" loading={isPending} disabled={target === undefined || sendableCount === 0}>
            보내기
          </Button>
        </Space>
      </form>
      {state.status === "failed" ? <ErrorViewAlert error={state.error} /> : null}
      {state.status === "idle" ? null : <CsvResultTable results={state.results} />}
    </Space>
  );
}
