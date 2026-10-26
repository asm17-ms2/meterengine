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
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  month: string;
  allowAll?: boolean;
}) {
  const { query: liveQuery, push } = useListNavigation(query, capabilities);
  const month = liveQuery.month ?? committedMonth;
  const { token } = theme.useToken();

  function handleChange(value: Dayjs | null) {
    if (!value && !allowAll) return;
    push({ month: value ? value.format(MONTH_FORMAT) : ALL_MONTHS });
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
      picker="month"
      format={MONTH_FORMAT}
      aria-label="조회 기간"
      value={month === ALL_MONTHS ? null : dayjs(`${month}-01`)}
      allowClear={allowAll ? { clearIcon: <CloseCircleFilled aria-label="지우기" /> } : false}
      placeholder={allowAll ? "전체 기간" : undefined}
      suffixIcon={<CalendarOutlined aria-hidden aria-label={undefined} />}
      cellRender={renderCell}
      onChange={handleChange}
    />
  );
}
