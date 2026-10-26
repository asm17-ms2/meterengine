"use client";

import { CaretDownOutlined, CaretUpOutlined } from "@ant-design/icons";
import type { TableColumnType } from "antd";

type SortOrder = TableColumnType<unknown>["sortOrder"];

const SORTER_CLASS = "ant-table-column-sorter";

export function SortIcon({ sortOrder }: { sortOrder: SortOrder }) {
  return (
    <span className={`${SORTER_CLASS} ${SORTER_CLASS}-full`}>
      <span className={`${SORTER_CLASS}-inner`} aria-hidden>
        <CaretUpOutlined
          aria-hidden
          aria-label={undefined}
          className={sortOrder === "ascend" ? `${SORTER_CLASS}-up active` : `${SORTER_CLASS}-up`}
        />
        <CaretDownOutlined
          aria-hidden
          aria-label={undefined}
          className={sortOrder === "descend" ? `${SORTER_CLASS}-down active` : `${SORTER_CLASS}-down`}
        />
      </span>
    </span>
  );
}
