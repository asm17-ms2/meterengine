import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 기본 위치(bottom-left)가 사이드바 하단의 개발 모드 표 상태 스위치를 덮는다.
  devIndicators: { position: "bottom-right" },

  // rewrites()로 백엔드를 프록시하지 않는다. rewrites는 요청 헤더를 주입할 수 없어서
  // X-Organization-Id를 브라우저가 붙여야 하고, 그러면 조회 대상 도입사가
  // 클라이언트에 노출된다. 백엔드 호출은 Server Component에서 서버 사이드로 나간다
  // (src/lib/api/client.ts).
};

export default nextConfig;
