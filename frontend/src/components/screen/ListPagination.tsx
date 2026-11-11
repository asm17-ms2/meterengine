"use client";

import { DoubleLeftOutlined, DoubleRightOutlined, EllipsisOutlined } from "@ant-design/icons";
import { Pagination, theme, type PaginationProps } from "antd";
import koreanLocale from "antd/locale/ko_KR";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { usePathname, useRouter } from "next/navigation";
import { cloneElement, isValidElement } from "react";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";
import type { ListCapabilities } from "@/lib/api/capabilities";
import { buildListHref, type ListQuery } from "@/lib/list-query";
import { workspaceHref } from "@/lib/workspace-href";

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

function useLinkItemRender(current: number, hrefFor: (page: number) => string): ItemRender {
  const { token } = theme.useToken();
  const renderItem: ItemRender = function renderItem(page, type, originalElement) {
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
        href={hrefFor(page)}
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
  return renderItem;
}

export function ListPagination({
  query,
  capabilities,
  total,
  isSimple = false,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  total?: number;
  isSimple?: boolean;
}) {
  const pathname = usePathname();
  const { query: linkQuery, push } = useListNavigation(query, capabilities);
  const current = linkQuery.page + 1;
  const renderItem = useLinkItemRender(current, (page) => buildListHref(pathname, linkQuery, { page: page - 1 }));

  // 간단 모양은 0건일 때 "1 / 0"이 되므로 그리지 않는다.
  if (!capabilities.page || total === undefined || (isSimple && total === 0)) return null;

  return (
    <nav aria-label="페이지">
      <Pagination
        current={current}
        total={total}
        pageSize={linkQuery.size}
        showSizeChanger={false}
        showLessItems
        simple={isSimple}
        itemRender={renderItem}
        onChange={(page) => push({ page: page - 1 })}
      />
    </nav>
  );
}

// 총 건수만 주는 목록(ListQuery를 쓰지 않는 화면)의 페이지 링크. page는 0부터 센다.
export function PageLinks({
  pathname,
  params,
  page,
  size,
  total,
  label,
}: {
  pathname: string;
  params: Record<string, string>;
  page: number;
  size: number;
  total: number;
  label: string;
}) {
  const router = useRouter();
  const scope = useWorkspaceSearch().toString();
  const hrefFor = (next: number) => {
    const search = new URLSearchParams(params);
    search.set("page", String(next - 1));
    return `${pathname}?${search}`;
  };
  const renderItem = useLinkItemRender(page + 1, hrefFor);

  return (
    <nav aria-label={label}>
      <Pagination
        align="end"
        current={page + 1}
        total={total}
        pageSize={size}
        showSizeChanger={false}
        showLessItems
        itemRender={renderItem}
        onChange={(next) => router.push(workspaceHref(hrefFor(next), scope))}
      />
    </nav>
  );
}
