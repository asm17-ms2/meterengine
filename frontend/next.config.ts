import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  devIndicators: { position: "bottom-right" },

  allowedDevOrigins: ["*.*.*.*"],
};

export default nextConfig;
