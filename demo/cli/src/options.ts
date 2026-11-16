import type { ArchiveStatus } from "@meterengine/sdk";
import { UsageError } from "./exit-code.ts";

export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ARCHIVE_STATUSES: readonly string[] = ["active", "archived", "all"];

export const STATUS_OPTION_DESCRIPTION = "보관 상태: active, archived, all. 생략하면 서버 기본값 active";

// 서버가 400으로 거절할 값을 보내기 전에 거른다. 외부 ID를 넣는 실수가 잦아 찾는 법을 함께 알린다.
export function assertCustomerId(id: string): string {
  if (!UUID_PATTERN.test(id)) {
    throw new UsageError("invalid_customer_id", "--customer에는 MeterEngine 고객 UUID를 넣습니다. 외부 ID로 UUID를 찾으려면 customers list --external-id <id>");
  }
  return id;
}

export function parseArchiveStatus(value: string): ArchiveStatus {
  if (!ARCHIVE_STATUSES.includes(value)) throw new UsageError("invalid_status", "--status는 active, archived, all 중 하나입니다");
  return value as ArchiveStatus;
}

export function requireArgument(value: string, name: string): string {
  if (value === "") throw new UsageError("invalid_usage", `${name}가 비었습니다`);
  return value;
}
