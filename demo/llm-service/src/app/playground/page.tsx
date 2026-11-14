import type { Metadata } from "next";
import { OpenAbApplication } from "@/components/OpenAbApplication";

export const metadata: Metadata = { title: "API 플레이그라운드" };

export default function PlaygroundPage() {
  return <OpenAbApplication section="api" />;
}
