"use client";

import { useRouter } from "next/navigation";

export function RetryButton() {
  const router = useRouter();
  return (
    <button
      type="button"
      className="btn btn-primary"
      onClick={() => router.refresh()}
    >
      다시 시도
    </button>
  );
}
