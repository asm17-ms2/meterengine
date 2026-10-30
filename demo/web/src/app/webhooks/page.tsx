import { Alert, Button } from "antd";
import { connection } from "next/server";

import { WebhookList } from "@/components/WebhookList";
import { parseTargets, type Target } from "@/lib/config";
import { isWebhookSecretUsable, type WebhookReceipt } from "@/lib/webhook-receipt";
import { listWebhookReceipts } from "@/lib/webhook-store";
import { selectWebhookTarget } from "@/lib/webhook-target";

const WEBHOOK_RECEIVE_URL = "http://127.0.0.1:3100/api/webhooks";
const ENVIRONMENT_LABELS = { legacy: "기존 데이터", test: "테스트 환경", live: "실제 환경" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function WebhooksPage({ searchParams }: { searchParams: SearchParams }) {
  await connection();
  const params = await searchParams;
  let targets: Target[];
  try { targets = parseTargets(process.env); }
  catch { return <Alert type="error" title="연동 대상 설정을 읽지 못했습니다" description="서버의 METERENGINE_TARGETS 설정을 확인하세요." />; }
  const values = params.target === undefined ? [] : Array.isArray(params.target) ? params.target : [params.target];
  const selected = selectWebhookTarget(values, process.env);
  const selector = <form action="/webhooks" method="get" style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
    <label style={{ display: "flex", gap: 8, alignItems: "center" }}>연동 대상<select name="target" aria-label="웹훅 연동 대상" defaultValue={selected.ok ? selected.value.target.name : ""} required style={{ padding: 8, minWidth: 190, maxWidth: "100%", minHeight: 44 }}>
      <option value="">대상을 선택하세요</option>
      {targets.map(target => <option key={target.name} value={target.name}>{target.name} / {ENVIRONMENT_LABELS[target.environment ?? "legacy"]}</option>)}
    </select></label><Button htmlType="submit" style={{ minHeight: 44 }}>대상 보기</Button>
  </form>;
  if (!selected.ok) return <div className="webhook-page" style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr)", gap: 16, minWidth: 0 }}>{selector}{values.length === 0 ? <p>연동 대상을 선택하면 해당 대상의 수신 URL과 기록을 볼 수 있습니다.</p> : <Alert type="error" title="연동 대상을 다시 선택하세요" description="설정된 대상을 하나만 선택해야 합니다." />}</div>;
  const { target, scope, secret } = selected.value;
  const receiveUrl = new URL(WEBHOOK_RECEIVE_URL);
  receiveUrl.searchParams.set("target", target.name);
  let receipts: WebhookReceipt[] = [];
  let loadError: string | undefined;
  try { receipts = await listWebhookReceipts(scope); }
  catch { loadError = "선택한 대상의 수신 기록을 읽지 못했습니다. 서버의 대상 설정과 기록 저장소를 확인하세요."; }
  return <div className="webhook-page" style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr)", gap: 16, minWidth: 0 }}>{selector}<WebhookList targetName={target.name} environmentLabel={ENVIRONMENT_LABELS[scope.environment]} receiveUrl={receiveUrl.href} hasSecret={isWebhookSecretUsable(secret)} receipts={receipts} loadError={loadError} /></div>;
}
