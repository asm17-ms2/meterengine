import { ScopedLink } from "@/components/workspace/ScopedLink";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError } from "@/components/workspace/WorkspaceView";
import { type AuditLogsResponse, listOrganizationMembers } from "@/lib/api/management";
import { operatorRequest, listOrganizations, listUserAccounts } from "@/lib/api/operator";
import { workspaceFetch } from "@/lib/api/workspace";
import { AUDIT_ACTOR_TYPES, auditActorLabel } from "@/lib/audit";
import { formatKstDateTime } from "@/lib/format";

const ACTIONS: Record<string, string> = {
  api_request_completed: "API 요청", session_created: "로그인", session_deleted: "로그아웃", signup_created: "도입사 가입 신청",
  organization_status_updated: "도입사 상태 변경", user_account_created: "운영자 계정 생성", user_account_role_updated: "플랫폼 권한 변경", organization_member_created: "사용자 소속 추가",
  organization_member_archived: "사용자 소속 보관", organization_member_role_updated: "도입사 역할 변경", organization_invitation_created: "관리자 초대",
  organization_invitation_accepted: "초대 수락", organization_invitation_revoked: "초대 취소", api_key_created: "API 키 발급", api_key_deleted: "API 키 폐기",
  invoice_finalized: "인보이스 확정", price_rate_created: "단가 버전 등록", price_rate_deleted: "단가 적용 종료", price_plan_created: "요금제 등록",
  invoice_view_link_created: "고객 조회 링크 발급", invoice_view_link_revoked: "고객 조회 링크 폐기", invoice_viewed: "고객 청구서 조회",
  payment_retry_requested: "고객 결제 재시도 요청",
  customer_created: "고객 등록", customer_updated: "고객 정보 변경", customer_archived: "고객 보관", customer_restored: "고객 복원",
  billable_metric_created: "미터 등록", billable_metric_updated: "미터 수정", billable_metric_archived: "미터 보관", billable_metric_restored: "미터 복원", price_rate_archived: "단가 보관", price_rate_restored: "단가 복원",
  price_plan_archived: "요금제 보관", price_plan_restored: "요금제 복원", payment_attempt_created: "결제 요청", payment_cancellation_created: "결제 취소",
};

type Query = Record<string, string | string[] | undefined>;
function value(query: Query, key: string) { return typeof query[key] === "string" ? query[key] as string : ""; }
function apiTime(input: string) { return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(input) ? input + "+09:00" : input; }
function localTime(input: string) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(input)) return input + "T00:00:00";
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(input)) return input;
  const time = Date.parse(input);
  return Number.isFinite(time) ? new Date(time + 9 * 3600_000).toISOString().slice(0, 19) : "";
}

export async function AuditScreen({ query, isOperator = false }: { query: Query; isOperator?: boolean }) {
  const params = new URLSearchParams();
  for (const key of ["action", "actor_type", "q", "from", "to", "sort", "page", "size"]) {
    const input = value(query, key);
    if (input) params.set(key, key === "from" || key === "to" ? apiTime(input) : input);
  }
  const path = `${isOperator ? "/v1/operations/audit-logs" : "/v1/audit-logs"}?${params}`;
  const [result, people, organizations] = await Promise.all([
    isOperator ? operatorRequest<AuditLogsResponse>(path) : workspaceFetch<AuditLogsResponse>(path),
    isOperator ? listUserAccounts() : listOrganizationMembers(),
    isOperator ? listOrganizations() : Promise.resolve(null),
  ]);
  const names = new Map<string, string>();
  if (people.ok) {
    if ("members" in people.data) for (const member of people.data.members) names.set(member.user_account_id, `${member.name} (${member.email})`);
    else for (const user of people.data.user_accounts) names.set(user.id, `${user.name} (${user.email})`);
  }
  const orgNames = new Map(organizations?.ok ? organizations.data.organizations.map(org => [org.id, org.name]) : []);
  const pagePath = isOperator ? "/admin/audit" : "/audit";
  function pageHref(page: number) { const next = new URLSearchParams(params); next.set("page", String(page)); return `${pagePath}?${next}`; }
  return <>
    <WorkspaceHeader title="감사 기록" kicker={isOperator ? "MeterEngine 플랫폼 운영" : "도입사 설정"} description={isOperator ? "전체 도입사의 업무 변경과 API 요청 결과를 확인하세요." : "누가 무엇을 바꿨는지, 어떤 API 요청이 성공하거나 거절됐는지 확인하세요."} />
    <section className="workspace-panel"><h2>기록 찾기</h2><form method="get" className="workspace-form">
      <input type="hidden" name="organization_id" value={value(query, "organization_id")} />
      <div className="settings-grid">
        <label className="workspace-field"><span>행동 또는 대상 검색</span><input name="q" defaultValue={value(query, "q")} placeholder="행동 코드, 대상 ID 또는 API 경로" maxLength={255} /></label>
        <div className="workspace-field"><label htmlFor="audit-actor">실행 주체</label><select id="audit-actor" name="actor_type" defaultValue={value(query, "actor_type")}><option value="">모든 주체</option>{Object.entries(AUDIT_ACTOR_TYPES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></div>
        <div className="workspace-field"><label htmlFor="audit-action">행동</label><select id="audit-action" name="action" defaultValue={value(query, "action")}><option value="">모든 행동</option>{Object.entries(ACTIONS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></div>
        <div className="workspace-field"><label htmlFor="audit-sort">정렬</label><select id="audit-sort" name="sort" defaultValue={value(query, "sort") || "occurred_at_desc"}><option value="occurred_at_desc">최신 기록부터</option><option value="occurred_at_asc">오래된 기록부터</option></select></div>
        <label className="workspace-field"><span>시작 일시 (KST)</span><input name="from" type="datetime-local" step="1" defaultValue={localTime(value(query, "from"))} /></label>
        <label className="workspace-field"><span>종료 일시 (KST, 미포함)</span><input name="to" type="datetime-local" step="1" defaultValue={localTime(value(query, "to"))} /></label>
      </div><div className="workspace-actions"><button className="month-apply" type="submit">조건으로 조회</button><ScopedLink href={pagePath}>조건 초기화</ScopedLink></div>
    </form></section>
    {!result.ok ? <WorkspaceError message={result.error.message} /> : <section className="workspace-panel"><div className="workspace-heading"><h2>활동 {new Intl.NumberFormat("ko-KR").format(result.data.total)}건</h2><p className="field-hint">시간은 KST입니다. 조회 요청도 감사 대상이므로 새로 조회하면 기록이 추가됩니다.</p></div>
      {result.data.audit_logs.length === 0 ? <p className="muted">이 조건에 해당하는 기록이 없습니다. 기간이나 행동 조건을 조정하세요.</p> : <div className="audit-list">{result.data.audit_logs.map(log => {
        const status = Number(log.detail.status);
        const outcome = log.action === "api_request_completed" && Number.isFinite(status) ? status >= 400 ? `요청 거절/오류 (${status})` : `성공 (${status})` : "변경 기록";
        return <details className="audit-entry" key={log.id}><summary className="audit-summary"><time dateTime={log.occurred_at}>{formatKstDateTime(log.occurred_at)}</time><strong>{ACTIONS[log.action] ?? log.action}</strong><span>{auditActorLabel(log.actor_type, log.actor_id, names)}</span><span>{outcome}</span>{log.action === "api_request_completed" && <span className="audit-request">{String(log.detail.method ?? "")} {String(log.detail.route ?? "")}</span>}</summary><div className="audit-detail"><dl className="audit-meta">
          <div><dt>실행 주체</dt><dd>{log.actor_id ? names.get(log.actor_id) ?? `${AUDIT_ACTOR_TYPES[log.actor_type]} ${log.actor_id}` : AUDIT_ACTOR_TYPES[log.actor_type]}</dd></div>
          {isOperator && <div><dt>도입사</dt><dd>{log.organization_id ? orgNames.get(log.organization_id) ?? log.organization_id : "도입사 미지정"}</dd></div>}
          <div><dt>대상</dt><dd>{log.target_type}: {log.target_id}</dd></div><div><dt>행동 코드</dt><dd><code>{log.action}</code></dd></div>
        </dl><pre className="workspace-json">{JSON.stringify(log.detail, null, 2)}</pre></div></details>;
      })}</div>}
      <nav className="workspace-actions" aria-label="감사 기록 페이지">{result.data.page > 0 && <ScopedLink href={pageHref(result.data.page - 1)}>이전 기록</ScopedLink>}<span>{result.data.page + 1}페이지</span>{(result.data.page + 1) * result.data.size < result.data.total && <ScopedLink href={pageHref(result.data.page + 1)}>다음 기록</ScopedLink>}</nav>
    </section>}
  </>;
}
