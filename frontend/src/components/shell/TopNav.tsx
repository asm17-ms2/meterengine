import { config } from "@/lib/config";

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
