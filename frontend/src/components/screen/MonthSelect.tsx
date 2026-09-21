"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import type { MonthOption } from "@/lib/month";

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

  function handleChange(nextMonth: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("month", nextMonth);
    params.delete("page");
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <select
      className="input"
      style={{ width: "auto" }}
      aria-label="조회 기간"
      value={value}
      onChange={(event) => handleChange(event.target.value)}
    >
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          기간: {option.label}
        </option>
      ))}
    </select>
  );
}
