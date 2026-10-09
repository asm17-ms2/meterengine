"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

export type CustomerOption = { value: string; label: string };

export function CustomerSelect({
  value,
  options,
  disabled,
}: {
  /** 고객 id. 빈 문자열이면 전체 고객. */
  value: string;
  options: CustomerOption[];
  disabled?: boolean;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function handleChange(nextCustomerId: string) {
    const params = new URLSearchParams(searchParams.toString());
    if (nextCustomerId === "") params.delete("customer_id");
    else params.set("customer_id", nextCustomerId);
    params.delete("page");
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <select
      className="input"
      style={{ width: "auto", maxWidth: 320 }}
      aria-label="고객"
      value={value}
      disabled={disabled}
      onChange={(event) => handleChange(event.target.value)}
    >
      <option value="">고객: 전체</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          고객: {option.label}
        </option>
      ))}
    </select>
  );
}
