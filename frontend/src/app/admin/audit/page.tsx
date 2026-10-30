import { AuditScreen } from "@/components/workspace/AuditScreen";

export default async function OperatorAuditPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  return <AuditScreen query={await searchParams} isOperator />;
}
