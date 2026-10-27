import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  logging: { incomingRequests: false, serverFunctions: false },

  devIndicators: { position: "bottom-right" },

  allowedDevOrigins: ["*.*.*.*"],
  async headers() {
    return [{ source: "/invoice-links/:path*", headers: [
      { key: "Referrer-Policy", value: "no-referrer" },
      { key: "X-Robots-Tag", value: "noindex, nofollow, noarchive" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Content-Security-Policy", value: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'" },
    ] }];
  },
};

export default nextConfig;
