import { MeterLabel } from "@/components/table/MeterLabel";

export function MeterGroupRow({
  columns,
  label,
  labelTooltip,
  combinationCount,
  isCollapsed,
  onToggle,
  children,
}: {
  columns: string;
  label: string;
  labelTooltip?: string;
  combinationCount: number;
  isCollapsed: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      className="grid-row grid-row--clickable grid-row--meter"
      style={{ gridTemplateColumns: columns }}
      aria-expanded={!isCollapsed}
      onClick={onToggle}
    >
      <div className="grid-cell grid-cell--child grid-cell--mono">
        <span aria-hidden>{isCollapsed ? "▸" : "▾"}</span> <MeterLabel label={label} labelTooltip={labelTooltip} />
      </div>
      <div className="grid-cell grid-cell__count">조합 {combinationCount}개</div>
      {children}
    </button>
  );
}
