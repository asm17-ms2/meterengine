import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { workspaceFetch } from "@/lib/api/workspace";
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
  const body = <><p className="field-hint">{data.month}의 현재 수집 상태입니다. 미터별 판정이므로 같은 이벤트가 여러 항목에 포함될 수 있습니다.</p>{active.length ? list(active) : <p role="status">현재 확인할 수집 문제가 없습니다.</p>}{normal.length > 0 && <details><summary>문제 없는 항목 {normal.length}개 보기</summary>{list(normal)}</details>}<Link href="/integrations">처리 결과 통보 연결</Link></>;
  if (selectedMonth !== data.month) return <section className="workspace-panel"><details><summary>이번 달({data.month}) 수집 진단</summary><p className="field-hint">위 집계의 {selectedMonth}와 다른 기간입니다. 현재 수집 상태만 보여 줍니다.</p>{body}</details></section>;
  return <section className="workspace-panel"><h2>수집 확인과 정정</h2>{body}</section>;
}
