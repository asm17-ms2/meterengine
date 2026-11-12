import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "청구서 조회 | MeterEngine",
  description: "받은 링크로 청구서와 결제 상태를 확인합니다.",
  referrer: "no-referrer",
  robots: { index: false, follow: false, nocache: true },
};

export default function InvoiceLinksLayout({ children }: { children: React.ReactNode }) {
  return children;
}
