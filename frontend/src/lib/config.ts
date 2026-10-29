import "server-only";

export const config = {
  apiBaseUrl: process.env.METERENGINE_API_BASE_URL ?? "http://localhost:8080",
} as const;
