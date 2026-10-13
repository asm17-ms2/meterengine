"use client";

import Link from "next/link";
import { useState } from "react";

export function BillingFinalizedNotice({
  title,
  invoicesHref,
  zeroAmountCustomerCount,
  children,
}: {
  title: string;
  invoicesHref: string;
  zeroAmountCustomerCount: number;
  children: React.ReactNode;
}) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <div className="empty-state">
        <div className="empty-state__title">{title}</div>
        <p className="empty-state__body">
          청구 예정액이 0원인 고객 {zeroAmountCustomerCount}곳은 인보이스가 만들어지지 않았습니다.
        </p>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 4 }}>
          <Link className="btn btn-secondary" href={invoicesHref}>
            인보이스 보기
          </Link>
          <button
            type="button"
            className="btn btn-secondary"
            aria-expanded={isOpen}
            onClick={() => setIsOpen(!isOpen)}
          >
            0원 고객 {zeroAmountCustomerCount}곳 {isOpen ? "접기" : "보기"}
          </button>
        </div>
      </div>
      {isOpen ? children : null}
    </>
  );
}
