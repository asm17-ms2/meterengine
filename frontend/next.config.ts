import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  logging: { incomingRequests: false, serverFunctions: false },

  devIndicators: { position: "bottom-right" },

  allowedDevOrigins: ["*.*.*.*"],
  poweredByHeader: false,
  // 같은 키는 뒤 항목이 앞 항목을 덮어써, 고객 인보이스 링크는 아래 전용 CSP를 그대로 쓴다.
  async headers() {
    return [{ source: "/:path*", headers: [
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
      { key: "X-Content-Type-Options", value: "nosniff" },
    ] }, { source: "/invoice-links/:path*", headers: [
      { key: "Referrer-Policy", value: "no-referrer" },
      { key: "X-Robots-Tag", value: "noindex, nofollow, noarchive" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Content-Security-Policy", value: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'" },
    ] }];
  },
};

export default nextConfig;
