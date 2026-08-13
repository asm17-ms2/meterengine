import { config } from "@/lib/config";

/**
 * 상단 바. 도입사 이름과 ID는 설정에서 온다 - org를 조회하는 엔드포인트가 아직 없다.
 */
export function TopNav() {
  return (
    <div
      className="nav sticky top-0"
      style={{ height: 54, zIndex: 5, background: "var(--color-bg)" }}
    >
      <span className="nav-brand">MeterEngine</span>
      <span style={{ fontSize: 12, color: "var(--text-55)" }}>
        관리자 콘솔, {config.organizationName} ({config.organizationId})
      </span>
    </div>
  );
}
