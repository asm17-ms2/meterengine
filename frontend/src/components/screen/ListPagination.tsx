"use client";

import { DoubleLeftOutlined, DoubleRightOutlined, EllipsisOutlined } from "@ant-design/icons";
import { Pagination, theme, type PaginationProps } from "antd";
import koreanLocale from "antd/locale/ko_KR";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname } from "next/navigation";
import { cloneElement, isValidElement } from "react";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import type { ListCapabilities } from "@/lib/api/capabilities";
import { buildListHref, type ListQuery } from "@/lib/list-query";

type ItemRender = NonNullable<PaginationProps["itemRender"]>;

type ItemElementProps = React.AnchorHTMLAttributes<HTMLAnchorElement> & { children?: React.ReactNode };

const PAGINATION_CLASS = "ant-pagination";

function JumpIcon({ type }: { type: "jump-prev" | "jump-next" }) {
  const Icon = type === "jump-prev" ? DoubleLeftOutlined : DoubleRightOutlined;
  return (
    <div className={`${PAGINATION_CLASS}-item-container`} aria-hidden>
      <Icon className={`${PAGINATION_CLASS}-item-link-icon`} aria-hidden aria-label={undefined} />
      <span className={`${PAGINATION_CLASS}-item-ellipsis`}>
        <EllipsisOutlined aria-hidden aria-label={undefined} />
      </span>
    </div>
  );
}

const STEP_LABELS: Partial<Record<Parameters<ItemRender>[1], string>> = { prev: "이전", next: "다음" };

const JUMP_LABELS: Partial<Record<Parameters<ItemRender>[1], string>> = {
  "jump-prev": koreanLocale.Pagination?.prev_3,
  "jump-next": koreanLocale.Pagination?.next_3,
};

export function ListPagination({
  query,
  capabilities,
  total,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  total?: number;
}) {
  const pathname = usePathname();
  const { query: linkQuery, push } = useListNavigation(query, capabilities);
  const { token } = theme.useToken();

  if (!capabilities.page || total === undefined) return null;

  const current = linkQuery.page + 1;

  const renderItem: ItemRender = (page, type, originalElement) => {
    if (!isValidElement<ItemElementProps>(originalElement)) return originalElement;
    const stepLabel = STEP_LABELS[type];
    const jumpLabel = JUMP_LABELS[type];
    const stepStyle = { ...originalElement.props.style, paddingInline: token.paddingXS, fontSize: token.fontSize };
    const isDisabledStep = stepLabel !== undefined && page === current;
    if (page < 1 || isDisabledStep) {
      return stepLabel === undefined
        ? originalElement
        : cloneElement(originalElement, { style: stepStyle }, stepLabel);
    }
    return (
      <Link
        {...originalElement.props}
        type={undefined}
        style={stepLabel === undefined ? originalElement.props.style : { ...stepStyle, color: "inherit" }}
        href={buildListHref(pathname, linkQuery, { page: page - 1 })}
        tabIndex={-1}
        aria-label={jumpLabel}
        aria-current={type === "page" && page === current ? "page" : undefined}
        onClick={(event) => event.stopPropagation()}
      >
        {stepLabel ??
          (type === "jump-prev" || type === "jump-next" ? (
            <JumpIcon type={type} />
          ) : (
            originalElement.props.children
          ))}
      </Link>
    );
  };

  return (
    <nav aria-label="페이지">
      <Pagination
        current={current}
        total={total}
        pageSize={linkQuery.size}
        showSizeChanger={false}
        showLessItems
        itemRender={renderItem}
        onChange={(page) => push({ page: page - 1 })}
      />
    </nav>
  );
}
