"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import { useTenantAccess } from "@/components/auth/TenantAccess";

// 콘솔 틀은 화면을 옮길 때 다시 그려지지 않아, 승인된 뒤에도 업무 환경이 잠긴 것으로 남는다.
// 이용 신청 화면이 열린 조직을 보면 틀을 한 번 새로 고친다.
export function LiveEnvironmentShellRefresh() {
  const access = useTenantAccess();
  const router = useRouter();
  const hasRefreshed = useRef(false);
  useEffect(() => {
    if (access?.isLiveEnvironmentOpen !== false || hasRefreshed.current) return;
    hasRefreshed.current = true;
    router.refresh();
  }, [access, router]);
  return null;
}
