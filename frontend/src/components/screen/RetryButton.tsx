"use client";

import { useRouter } from "next/navigation";

/**
 * 서버 컴포넌트를 다시 실행한다. 데이터를 못 불러온 원인이 사라졌으면(백엔드 재기동 등)
 * 이걸로 복구된다.
 */
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
