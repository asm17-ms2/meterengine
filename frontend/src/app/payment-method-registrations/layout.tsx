import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "결제 카드 등록 | MeterEngine",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default function PaymentRegistrationLayout({ children }: { children: React.ReactNode }) {
  return children;
}
