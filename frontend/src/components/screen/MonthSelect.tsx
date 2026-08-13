"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import type { MonthOption } from "@/lib/month";

/**
 * 조회 월 select. 선택하면 ?month=를 바꿔 서버가 화면을 다시 그린다.
 * 월이 바뀌면 페이지 번호는 의미가 없어지므로 같이 지운다.
 */
export function MonthSelect({
  value,
  options,
}: {
  value: string;
  options: MonthOption[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function onChange(next: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("month", next);
    params.delete("page");
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <select
      className="input"
      style={{ width: "auto" }}
      aria-label="조회 기간"
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          기간: {option.label}
        </option>
      ))}
    </select>
  );
}
