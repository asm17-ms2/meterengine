"use client";

import type { TableProps } from "antd";

type ExpandIconRender<Row> = NonNullable<NonNullable<TableProps<Row>["expandable"]>["expandIcon"]>;

type ExpandIconProps<Row> = Parameters<ExpandIconRender<Row>>[0] & { label: string };

export function ExpandIcon<Row>({
  prefixCls,
  expanded,
  expandable,
  record,
  onExpand,
  label,
}: ExpandIconProps<Row>) {
  const iconClass = `${prefixCls}-row-expand-icon`;

  if (!expandable) {
    return (
      <button type="button" className={`${iconClass} ${iconClass}-spaced`} tabIndex={-1} aria-hidden />
    );
  }

  return (
    <button
      type="button"
      className={`${iconClass} ${iconClass}-${expanded ? "expanded" : "collapsed"}`}
      aria-expanded={expanded}
      aria-label={`${label} ${expanded ? "접기" : "펼치기"}`}
      onClick={(event) => {
        event.stopPropagation();
        onExpand(record, event);
      }}
    />
  );
}

export function labeledExpandIcon<Row>(toLabel: (row: Row) => string): ExpandIconRender<Row> {
  return function LabeledExpandIcon(props) {
    return <ExpandIcon {...props} label={toLabel(props.record)} />;
  };
}
