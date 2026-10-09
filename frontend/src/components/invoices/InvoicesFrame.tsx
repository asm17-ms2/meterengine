import { CustomerSelect, type CustomerOption } from "@/components/screen/CustomerSelect";
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
  customerId,
  customerOptions,
  lockedCustomerOption,
  meta,
  children,
}: {
  /** `yyyy-MM`. 없으면 전체 기간. */
  month: string | undefined;
  /** 없으면 전체 고객. */
  customerId?: string;
  /** 없으면 고객 목록이 아직 없거나 못 받은 것이라 select를 잠근다. */
  customerOptions?: CustomerOption[];
  /** 고객 목록을 못 받았을 때 잠긴 select에 보일 현재 필터의 고객. */
  lockedCustomerOption?: CustomerOption;
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
        {customerOptions ? (
          <CustomerSelect value={customerId ?? ""} options={customerOptions} />
        ) : (
          <CustomerSelect
            value={lockedCustomerOption?.value ?? ""}
            options={lockedCustomerOption ? [lockedCustomerOption] : []}
            disabled
          />
        )}
      </FilterBar>
      {children}
    </>
  );
}
