import { FilterBar } from "@/components/screen/FilterBar";
import { ScreenHeader } from "@/components/screen/ScreenHeader";

/**
 * 목록이 없을 때의 제목 줄과 필터 행.
 *
 * 검색란을 비활성으로라도 두는 이유: 로딩과 에러에서 이 줄이 통째로 사라지면
 * 표가 그려질 때 화면이 위아래로 튄다. 걸러낼 목록이 없으니 쓸 수는 없다.
 * 제목 오른쪽의 "총 N명"과 등록 버튼은 뺀다 - 셀 수 있는 것이 없고, 목록도
 * 못 읽는 상태에서 등록만 열리면 저장한 결과를 확인할 수 없다.
 */
export function CustomersFrame({ children }: { children: React.ReactNode }) {
  return (
    <>
      <ScreenHeader title="고객" />
      <FilterBar>
        <input
          className="input"
          style={{ width: 340 }}
          type="search"
          aria-label="고객 이름 검색"
          placeholder="고객 이름 검색"
          disabled
        />
      </FilterBar>
      {children}
    </>
  );
}
