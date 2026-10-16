import { MeterLabel } from "@/components/table/MeterLabel";

export function MeterGroupRow({
  columns,
  label,
  labelTooltip,
  isCodeLabel = true,
  combinationCount,
  isCollapsed,
  onToggle,
  children,
}: {
  columns: string;
  label: string;
  labelTooltip?: string;
  isCodeLabel?: boolean;
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
      <div
        className={
          isCodeLabel ? "grid-cell grid-cell--child grid-cell--mono" : "grid-cell grid-cell--child"
        }
      >
        <span aria-hidden>{isCollapsed ? "▸" : "▾"}</span> <MeterLabel label={label} labelTooltip={labelTooltip} />
      </div>
      <div className="grid-cell grid-cell__count">조합 {combinationCount}개</div>
      {children}
    </button>
  );
}
