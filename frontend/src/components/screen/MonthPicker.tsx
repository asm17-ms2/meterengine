"use client";

import { CalendarOutlined, CloseCircleFilled } from "@ant-design/icons";
import { DatePicker, theme, type DatePickerProps } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { cloneElement, isValidElement } from "react";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";
import { ALL_MONTHS, formatKstMonth } from "@/lib/month";

const MONTH_FORMAT = "YYYY-MM";

type CellRender = NonNullable<DatePickerProps["cellRender"]>;

export function MonthPicker({
  query,
  capabilities,
  month: committedMonth,
  allowAll = false,
  onChange,
  id,
  label = "조회 기간",
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  month: string;
  allowAll?: boolean;
  onChange?: (month: string) => void;
  id?: string;
  label?: string;
}) {
  const { query: liveQuery, push } = useListNavigation(query, capabilities);
  const month = (onChange ? query.month : liveQuery.month) ?? committedMonth;
  const { token } = theme.useToken();

  function handleChange(value: Dayjs | null) {
    if (!value && !allowAll) return;
    const nextMonth = value ? value.format(MONTH_FORMAT) : ALL_MONTHS;
    if (onChange) onChange(nextMonth);
    else push({ month: nextMonth, from: undefined, to: undefined, rangeMode: undefined });
  }

  const renderCell: CellRender = (current, info) => {
    const isCurrentMonth =
      info.type === "month" &&
      dayjs.isDayjs(current) &&
      current.format(MONTH_FORMAT) === formatKstMonth();
    if (!isCurrentMonth || !isValidElement<{ style?: React.CSSProperties }>(info.originNode)) {
      return info.originNode;
    }
    return cloneElement(info.originNode, {
      style: { ...info.originNode.props.style, boxShadow: `inset 0 0 0 1px ${token.colorPrimary}` },
    });
  };

  return (
    <DatePicker
      id={id}
      picker="month"
      format={MONTH_FORMAT}
      aria-label={label}
      value={month === ALL_MONTHS ? null : dayjs(`${month}-01`)}
      allowClear={allowAll ? { clearIcon: <CloseCircleFilled aria-label="지우기" /> } : false}
      placeholder={allowAll ? "전체 기간" : undefined}
      suffixIcon={<CalendarOutlined aria-hidden aria-label={undefined} />}
      cellRender={renderCell}
      onChange={handleChange}
    />
  );
}
