export function MeterLabel({ label, labelTooltip }: { label: string; labelTooltip?: string }) {
  return (
    <span className="hover-hint" data-hint={labelTooltip}>
      {label}
    </span>
  );
}
