import type { Metadata } from "next";
import { OpenAbApplication } from "@/components/OpenAbApplication";

export const metadata: Metadata = { title: "청구 내역" };

export default async function BillingPage({ searchParams }: { searchParams: Promise<{ month?: string | string[] }> }) {
  const query = await searchParams;
  const month = typeof query.month === "string" && /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(query.month) ? query.month : undefined;
  return <OpenAbApplication section="billing" initialMonth={month} />;
}
