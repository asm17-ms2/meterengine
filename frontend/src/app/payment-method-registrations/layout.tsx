import type { Metadata } from "next";

export const metadata: Metadata = {
  title: { absolute: "결제 카드 등록" },
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default function PaymentRegistrationLayout({ children }: { children: React.ReactNode }) {
  return children;
}
