import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { workspaceFetch } from "@/lib/api/workspace";
import { formatKoreanMonth } from "@/lib/format";
import { WorkspaceError } from "@/components/workspace/WorkspaceView";
import { usageQualityReasons, type UnbilledEventCounts } from "@/lib/usage-quality";


export async function UsageQuality({ selectedMonth }: { selectedMonth: string }) {
  const result = await workspaceFetch<UnbilledEventCounts>("/v1/unbilled-events", { month: selectedMonth });
  if (!result.ok) return <section className="workspace-panel"><h2>수집 진단</h2><WorkspaceError message={result.error.message} /></section>;
  const data = result.data;
  const reasons = usageQualityReasons(data);
  const active = reasons.filter(reason => reason.count > 0);
  const normal = reasons.filter(reason => reason.count === 0);
  const list = (items: typeof reasons) => <ul className="setup-list">{items.map(reason => <li key={reason.title}><div><strong>{reason.title} <span className="numeric">{reason.count.toLocaleString("ko-KR")}건</span></strong><p>{reason.detail}</p></div>{reason.count > 0 && <Link href={reason.href}>{reason.action}</Link>}</li>)}</ul>;
  const body = <><p className="field-hint">{formatKoreanMonth(data.month)} 기준 지금 상태예요. 미터마다 따로 판정해서 한 이벤트가 여러 항목에 들어갈 수 있어요.</p>{active.length ? list(active) : <p role="status">지금 확인할 수집 문제가 없어요.</p>}{normal.length > 0 && <details><summary className="disclosure-summary">문제 없는 항목 {normal.length}개 보기</summary>{list(normal)}</details>}<Link href="/integrations">과금 제외 사용량 통보 설정</Link></>;
  if (selectedMonth !== data.month) return <section className="workspace-panel"><details><summary className="disclosure-summary"><h2>이번 달({formatKoreanMonth(data.month)}) 수집 진단</h2></summary><p className="field-hint">아래 표는 {formatKoreanMonth(selectedMonth)} 사용량이에요. 이 진단은 이번 달 상태만 보여 줘요.</p>{body}</details></section>;
  return <section className="workspace-panel"><h2>수집 진단</h2>{body}</section>;
}
