"use client";

import { GridCell, GridHead, GridRow, GridTable } from "@/components/table/Grid";

/**
 * 화면이 그리는 뷰모델. 등록일은 서버에서 이미 포맷해 온다.
 * 클라이언트에서 Intl을 부르면 브라우저 시간대가 KST가 아닐 때 서버와 값이 달라져
 * 하이드레이션이 어긋난다 (EventRowView와 같은 이유).
 */
export type CustomerRowView = {
  id: string;
  name: string;
  /** `2026-08-14` (KST). */
  createdAt: string;
};

/*
 * 고객 ID 열이 320px인 이유: UUID 전체가 한 줄에 들어가야 한다. 이 값은 이벤트를
 * 보낼 때 실어야 하는 식별자라 잘리면 옮겨 적을 수가 없다 (MS2-170에서 집계와
 * 청구 화면의 잘린 UUID를 전체 표시로 되돌린 것과 같은 판단이다).
 */
const COLUMNS = "minmax(0, 1fr) 320px 130px 100px";
const MIN_WIDTH = 860;

const HEAD = [
  "고객명",
  "고객 ID",
  "등록일",
  { label: "작업", right: true },
] as const;

export function CustomerTable({
  rows,
  onEdit,
  onDelete,
}: {
  rows: CustomerRowView[];
  onEdit: (row: CustomerRowView) => void;
  onDelete: (row: CustomerRowView) => void;
}) {
  return (
    <GridTable minWidth={MIN_WIDTH}>
      <GridHead columns={COLUMNS} labels={HEAD} />
      {rows.map((row) => (
        // 행 전체가 클릭 대상이 아니다. 행 안에 버튼이 둘 있어서 행까지 누를 수
        // 있게 하면 어디를 눌렀는지에 따라 다른 일이 일어난다.
        <GridRow key={row.id} columns={COLUMNS} className="grid-row--actions">
          <GridCell className="grid-cell--truncate grid-cell--strong">
            {row.name}
          </GridCell>
          <GridCell className="grid-cell--mono grid-cell--muted grid-cell--truncate">
            {row.id}
          </GridCell>
          <GridCell className="grid-cell--num grid-cell--muted">
            {row.createdAt}
          </GridCell>
          <GridCell className="grid-cell--actions">
            {/*
              버튼 글자가 '수정'뿐이라 스크린리더에는 같은 버튼이 줄 수만큼
              늘어선 것으로 읽힌다. aria-label로 어느 고객인지 붙인다.
            */}
            <button
              type="button"
              className="btn btn-ghost"
              style={{ fontSize: 13 }}
              aria-label={`${row.name} 수정`}
              onClick={() => onEdit(row)}
            >
              수정
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              style={{ fontSize: 13 }}
              aria-label={`${row.name} 삭제`}
              onClick={() => onDelete(row)}
            >
              삭제
            </button>
          </GridCell>
        </GridRow>
      ))}
    </GridTable>
  );
}
