import { CheckOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button } from "antd";
import type { GenerationView, WorkspaceSnapshot } from "@/lib/contracts";
import { formatTokens } from "./format";

export function Notice({ children, onRetry, isBusy = false, title }: { children: React.ReactNode; onRetry?: () => void; isBusy?: boolean; title?: string }) {
  return <div className="notice" role="alert"><div>{title && <strong>{title}</strong>}<div className="notice-body">{children}</div></div>{onRetry && <Button onClick={onRetry} loading={isBusy} icon={<ReloadOutlined aria-hidden="true" />}>다시 불러오기</Button>}</div>;
}

export function MeteringLabel({ value }: { value: GenerationView["meteringStatus"] }) {
  const label = value === "delivered" ? "사용량 기록됨" : value === "failed" ? "사용량 기록 확인 필요" : "사용량 기록 대기";
  return <span className={`state-label state-label-${value}`}>{value === "delivered" && <CheckOutlined aria-hidden="true" />}{label}</span>;
}

export function SyncStatus({ snapshot, isBusy, onRetry }: { snapshot: WorkspaceSnapshot; isBusy: boolean; onRetry: () => void }) {
  const total = snapshot.stats.pendingMetering + snapshot.stats.failedMetering;
  if (total === 0) return <p className="sync-ready"><CheckOutlined aria-hidden="true" /> {snapshot.stats.totalRequests > 0 ? "이번 달 요청의 사용량을 모두 기록했어요." : "첫 요청을 보내면 사용량이 기록돼요."}</p>;
  return <div className="sync-pending" role="status"><div><strong>사용량 {formatTokens(total)}건을 아직 기록하지 못했어요.</strong><p>응답은 저장했어요. 같은 사용량을 다시 보내도 두 번 청구하지 않아요.{snapshot.stats.failedMetering > 0 && ` 확인이 필요한 요청은 ${formatTokens(snapshot.stats.failedMetering)}건이에요.`}</p></div><Button onClick={onRetry} loading={isBusy}>사용량 다시 보내기</Button></div>;
}
