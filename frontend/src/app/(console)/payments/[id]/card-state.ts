export type PaymentReadiness = "usable" | "card-missing" | "key-missing" | "unknown";

type Lookup<T> = { ok: true; data: T } | { ok: false; error: { status: number } };

export function paymentReadiness(mode: string | undefined, keys: Lookup<{ toss_payments_credentials: { mode: string }[] }>, card: Lookup<{ archived_at?: string | null }>): PaymentReadiness {
  if (keys.ok && mode && !keys.data.toss_payments_credentials.some(key => key.mode === mode)) return "key-missing";
  if (card.ok) return card.data.archived_at ? "card-missing" : "usable";
  return card.error.status === 404 ? "card-missing" : "unknown";
}
