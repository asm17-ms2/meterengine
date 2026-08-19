import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Docker 실행 이미지(frontend/Dockerfile)가 이 출력만 담는다. next build가 실제로
  // 도달하는 모듈만 추려 .next/standalone에 self-contained 서버를 만들어 주므로,
  // 이미지에 pnpm과 node_modules 전체를 넣지 않아도 된다.
  //
  // next dev에는 영향이 없다. 다만 next start(pnpm start)는 이 설정과 같이 쓰지 말라고
  // Next가 경고한다. 실측하면 경고를 내고도 200을 주지만, 지원하는 조합이 아니므로
  // 프로덕션 실행은 node .next/standalone/server.js를 쓴다 (Dockerfile의 CMD가 그것이다).
  output: "standalone",

  // 기본 위치(bottom-left)가 사이드바 하단의 개발 모드 표 상태 스위치를 덮는다.
  devIndicators: { position: "bottom-right" },

  // 다른 기계에서 이 dev 서버를 볼 때 필요하다. Next 16은 localhost가 아닌 출처에서
  // 오는 /_next/* 요청을 기본으로 막는데, 거기에 클라이언트 JS 청크가 포함된다. 그래서
  // 서버가 만든 HTML은 그대로 보이지만 하이드레이션이 안 돼 버튼이 전부 죽는다.
  // 화면이 멀쩡해 보여서 원인을 찾기 어렵다.
  //
  // 별 네 개인 이유: 매처가 점으로 나뉜 마디 수를 맞춘다. '*'나 '**' 하나로는
  // IP의 네 마디를 못 덮는다(실측). 반대로 이 패턴은 마디가 넷인 출처만 허용하므로
  // example.com 같은 보통 도메인은 여전히 막힌다. LAN과 Tailscale 주소만 열린다.
  //
  // 개발 서버에만 적용되는 설정이다. next build/start에는 영향이 없다.
  allowedDevOrigins: ["*.*.*.*"],

  // rewrites()로 백엔드를 프록시하지 않는다. rewrites는 요청 헤더를 주입할 수 없어서
  // X-Organization-Id를 브라우저가 붙여야 하고, 그러면 조회 대상 도입사가
  // 클라이언트에 노출된다. 백엔드 호출은 Server Component에서 서버 사이드로 나간다
  // (src/lib/api/client.ts).
};

export default nextConfig;
