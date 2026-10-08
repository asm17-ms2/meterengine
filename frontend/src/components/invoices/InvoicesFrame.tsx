import { FilterBar } from "@/components/screen/FilterBar";
import { MonthSelect } from "@/components/screen/MonthSelect";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { formatKoreanMonth } from "@/lib/format";
import { buildMonthOptions, buildMonthOptionsFor, type MonthOption } from "@/lib/month";

const ALL_MONTHS_OPTION: MonthOption = { value: "", label: "전체" };

function toMonthOptions(month: string | undefined): MonthOption[] {
  return [ALL_MONTHS_OPTION, ...(month ? buildMonthOptionsFor(month) : buildMonthOptions())];
}

export function InvoicesFrame({
  month,
  meta,
  children,
}: {
  /** `yyyy-MM`. 없으면 전체 기간. */
  month: string | undefined;
  meta?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <>
      <ScreenHeader
        title={
          <>
            {month ? `인보이스 - ${formatKoreanMonth(month)}` : "인보이스"}{" "}
            <span className="tag tag-neutral">확정 (finalized)</span>
          </>
        }
      >
        {meta}
      </ScreenHeader>
      <FilterBar>
        <MonthSelect value={month ?? ""} options={toMonthOptions(month)} />
      </FilterBar>
      {children}
    </>
  );
}
