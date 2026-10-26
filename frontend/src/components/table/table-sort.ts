"use client";

import type { TableColumnType, TableProps } from "antd";
import { createElement, useState } from "react";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import { SortIcon } from "@/components/table/SortIcon";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery, SortDirection } from "@/lib/list-query";

type TableSortOrder = NonNullable<TableColumnType<unknown>["sortOrder"]>;

const SORT_DIRECTIONS: TableSortOrder[] = ["ascend", "descend"];

export type SortableColumn<Row> = TableColumnType<Row> & {
  sortKey?: string;
  clientSorter?: (left: Row, right: Row) => number;
};

export type TableSortState<Row> = {
  sortKey?: string;
  order?: SortDirection;
  compare?: (left: Row, right: Row) => number;
};

export type ClientSortSelection = {
  columnKey: React.Key;
  order: SortDirection;
};

export type ClientSortSelectionState = {
  selection: ClientSortSelection | undefined;
  onSelectionChange: (selection: ClientSortSelection | undefined) => void;
};

export type TableSortOptions = {
  isClientSortManual?: boolean;
  clientSortSelection?: ClientSortSelectionState;
};

type ClientSortColumn<Row> = {
  sortKey?: string;
  clientSorter: (left: Row, right: Row) => number;
};

const renderSortIcon: NonNullable<TableColumnType<unknown>["sortIcon"]> = ({ sortOrder }) =>
  createElement(SortIcon, { sortOrder });

function toTableSortOrder(order: SortDirection | undefined): TableSortOrder {
  return order === "desc" ? "descend" : "ascend";
}

function toSortDirection(order: TableSortOrder | null | undefined): SortDirection | undefined {
  if (order === "ascend") return "asc";
  if (order === "descend") return "desc";
  return undefined;
}

function toDirectedCompare<Row>(
  clientSorter: (left: Row, right: Row) => number,
  order: SortDirection,
): (left: Row, right: Row) => number {
  return order === "desc" ? (left, right) => clientSorter(right, left) : clientSorter;
}

export function useTableSort<Row>(
  query: ListQuery,
  capabilities: ListCapabilities,
  columns: SortableColumn<Row>[],
  options: TableSortOptions = {},
): {
  columns: TableColumnType<Row>[];
  onChange: NonNullable<TableProps<Row>["onChange"]>;
  sortState: TableSortState<Row>;
} {
  const { query: liveQuery, push } = useListNavigation(query, capabilities);
  const [ownClientSelection, setOwnClientSelection] = useState<ClientSortSelection | undefined>(
    undefined,
  );
  const clientSelection = options.clientSortSelection
    ? options.clientSortSelection.selection
    : ownClientSelection;
  const setClientSelection = options.clientSortSelection
    ? options.clientSortSelection.onSelectionChange
    : setOwnClientSelection;
  const isClientMode = !capabilities.sort && !capabilities.page;
  const sortKeyByColumnKey = new Map<React.Key, string>();
  const clientColumnByKey = new Map<React.Key, ClientSortColumn<Row>>();

  const tableColumns = columns.map(({ sortKey, clientSorter, ...column }): TableColumnType<Row> => {
    if (capabilities.sort && sortKey) {
      const columnKey = column.key ?? sortKey;
      sortKeyByColumnKey.set(columnKey, sortKey);
      return {
        ...column,
        key: columnKey,
        sorter: true,
        sortOrder: liveQuery.sort === sortKey ? toTableSortOrder(liveQuery.order) : null,
        sortDirections: SORT_DIRECTIONS,
        sortIcon: renderSortIcon,
      };
    }
    if (isClientMode && clientSorter) {
      const columnKey = column.key ?? sortKey;
      if (columnKey === undefined) return column;
      clientColumnByKey.set(columnKey, { sortKey, clientSorter });
      return {
        ...column,
        key: columnKey,
        sorter: options.isClientSortManual ? true : clientSorter,
        sortOrder:
          clientSelection?.columnKey === columnKey ? toTableSortOrder(clientSelection.order) : null,
        sortDirections: SORT_DIRECTIONS,
        sortIcon: renderSortIcon,
      };
    }
    return column;
  });

  const onChange: NonNullable<TableProps<Row>["onChange"]> = (_pagination, _filters, sorter, extra) => {
    if (extra.action !== "sort") return;
    const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
    const order = toSortDirection(activeSorter?.order);
    const columnKey = activeSorter?.columnKey;
    if (capabilities.sort) {
      const sortKey =
        order && columnKey !== undefined ? sortKeyByColumnKey.get(columnKey) : undefined;
      push({ sort: sortKey, order: sortKey ? order : undefined });
      return;
    }
    if (!isClientMode) return;
    setClientSelection(
      order && columnKey !== undefined && clientColumnByKey.has(columnKey)
        ? { columnKey, order }
        : undefined,
    );
  };

  const activeClientColumn =
    clientSelection === undefined ? undefined : clientColumnByKey.get(clientSelection.columnKey);
  const sortState: TableSortState<Row> =
    capabilities.sort
      ? { sortKey: liveQuery.sort, order: liveQuery.sort ? liveQuery.order : undefined }
      : clientSelection && activeClientColumn
        ? {
            sortKey: activeClientColumn.sortKey ?? String(clientSelection.columnKey),
            order: clientSelection.order,
            compare: toDirectedCompare(activeClientColumn.clientSorter, clientSelection.order),
          }
        : {};

  return { columns: tableColumns, onChange, sortState };
}
