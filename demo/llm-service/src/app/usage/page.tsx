import type { Metadata } from "next";
import { LumenApplication } from "@/components/LumenApplication";

export const metadata: Metadata = { title: "사용량" };

export default async function UsagePage({ searchParams }: { searchParams: Promise<{ month?: string | string[] }> }) {
  const query = await searchParams;
  const month = typeof query.month === "string" && /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(query.month) ? query.month : undefined;
  return <LumenApplication section="usage" initialMonth={month} />;
}
