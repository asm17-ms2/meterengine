import type { Metadata } from "next";
import { OpenAbApplication } from "@/components/OpenAbApplication";

export const metadata: Metadata = { title: "일괄 처리" };

export default function BatchPage() {
  return <OpenAbApplication section="batch" />;
}
