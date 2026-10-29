import Link from "next/link";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceError } from "@/components/workspace/WorkspaceView";

type UnbilledEventsResponse = { month: string; cutoff_at: string; unmatched_type: { event_count: number }; non_numeric_value: { event_count: number }; future_timestamp: { event_count: number }; negative_quantity: { event_count: number } };

export async function UsageQuality({ selectedMonth }: { selectedMonth: string }) {
  const result = await workspaceFetch<UnbilledEventsResponse>("/v1/unbilled-events", { month: selectedMonth });
  if (!result.ok) return <section className="workspace-panel"><h2>수집 진단</h2><WorkspaceError message={result.error.message} /></section>;
  const data = result.data;
  const reasons = [
    { title: "미터와 연결되지 않은 유형", count: data.unmatched_type.event_count, detail: "이벤트 유형에 맞는 미터를 등록하세요.", href: "/billable-metrics", action: "미터 설정" },
    { title: "집계할 수 없는 속성", count: data.non_numeric_value.event_count, detail: "집계 함수에 필요한 속성이 없거나 값 형식이 맞지 않아 사용량에서 제외됐습니다.", href: `/events?month=${data.month}`, action: "원본 확인" },
    { title: "미래 시각의 이벤트", count: data.future_timestamp.event_count, detail: "시각이 도래하기 전까지 집계에 포함하지 않습니다.", href: `/events?month=${data.month}`, action: "시각 확인" },
    { title: "음수 수량의 이벤트", count: data.negative_quantity.event_count, detail: "선택한 집계 함수에 따라 반영합니다. 집계값이 음수인 조합은 청구 검토에서 확인하세요.", href: `/billing?month=${data.month}`, action: "청구 검토" },
  ];
  const active = reasons.filter(reason => reason.count > 0);
  const normal = reasons.filter(reason => reason.count === 0);
  const list = (items: typeof reasons) => <ul className="setup-list">{items.map(reason => <li key={reason.title}><div><strong>{reason.title} <span className="numeric">{reason.count.toLocaleString("ko-KR")}건</span></strong><p>{reason.detail}</p></div>{reason.count > 0 && <Link href={reason.href}>{reason.action}</Link>}</li>)}</ul>;
  const body = <><p className="field-hint">{data.month}의 현재 수집 상태입니다. 미터별 판정이므로 같은 이벤트가 여러 항목에 포함될 수 있습니다.</p>{active.length ? list(active) : <p role="status">현재 확인할 수집 문제가 없습니다.</p>}{normal.length > 0 && <details><summary>문제 없는 항목 {normal.length}개 보기</summary>{list(normal)}</details>}<Link href="/integrations">처리 결과 통보 연결</Link></>;
  if (selectedMonth !== data.month) return <section className="workspace-panel"><details><summary>이번 달({data.month}) 수집 진단</summary><p className="field-hint">위 집계의 {selectedMonth}와 다른 기간입니다. 현재 수집 상태만 보여 줍니다.</p>{body}</details></section>;
  return <section className="workspace-panel"><h2>수집 확인과 정정</h2>{body}</section>;
}
