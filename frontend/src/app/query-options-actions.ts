"use server";

import { searchOptions, type QueryOption } from "@/lib/api/search";
import type { Result } from "@/lib/api/client";

export async function queryOptionsAction(resource: "customers" | "billable-metrics", q: string): Promise<Result<QueryOption[]>> {
  if (!["customers", "billable-metrics"].includes(resource) || typeof q !== "string" || q.length > 255) return { ok: false, error: { status: 400, code: "validation_error", message: "검색 조건을 255자 이내로 입력하세요." } };
  const result = await searchOptions(resource, q);
  return result.ok ? { ok: true, data: result.data.items } : result;
}
