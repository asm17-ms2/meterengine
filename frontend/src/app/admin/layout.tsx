import { requireOperator } from "@/lib/auth/session";
import { OperatorShell } from "@/components/shell/OperatorShell";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const session = await requireOperator();
  return <OperatorShell name={session.user_account.name}>{children}</OperatorShell>;
}
