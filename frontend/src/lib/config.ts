import "server-only";

export const config = {
  apiBaseUrl: process.env.METERENGINE_API_BASE_URL ?? "http://localhost:8080",
  organizationId:
    process.env.METERENGINE_ORGANIZATION_ID ??
    "d7cee55d-8c82-4afc-b996-6749d8b26a4e",
  organizationName: process.env.METERENGINE_ORGANIZATION_NAME ?? "데모 도입사",
} as const;
